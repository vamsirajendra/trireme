package com.apigee.noderunner.core.modules;

import com.apigee.noderunner.core.NodeEnvironment;
import com.apigee.noderunner.core.NodeException;
import com.apigee.noderunner.core.NodeModule;
import com.apigee.noderunner.core.internal.ModuleRegistry;
import com.apigee.noderunner.core.internal.ScriptRunner;
import com.apigee.noderunner.core.internal.Utils;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.FunctionObject;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.WrappedException;
import org.mozilla.javascript.annotations.JSFunction;
import org.mozilla.javascript.annotations.JSGetter;
import org.mozilla.javascript.annotations.JSSetter;
import org.mozilla.javascript.annotations.JSStaticFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;

/**
 * An implementation of the "module" package from node.js 0.8.15.
 */
public class Module
    implements NodeModule
{
    protected static final String CLASS_NAME = "_moduleClass";
    protected static final String OBJECT_NAME = "module";

    protected static final Logger log = LoggerFactory.getLogger(Module.class);

    // Stuff to set up this package
    @Override
    public String getModuleName() {
        return "module";
    }

    @Override
    public Object registerExports(Context cx, Scriptable scope, ScriptRunner runner)
        throws InvocationTargetException, IllegalAccessException, InstantiationException
    {
        ScriptableObject.defineClass(scope, ModuleImpl.class);
        ModuleImpl exports = (ModuleImpl)cx.newObject(scope, CLASS_NAME);
        exports.bindVariables(cx, scope, exports);
        return exports;
    }

    public static Object globalRequire(Context cx, Scriptable thisObj, Object[] args, Function func)
    {
        Scriptable module = (Scriptable)ScriptableObject.getProperty(thisObj, OBJECT_NAME);
        return ((ModuleImpl)module).require(cx, module, args, func);
    }

    public static class ModuleImpl
        extends ScriptableObject
    {
        private ScriptRunner runner;

        private Scriptable parentScope;
        private File file;
        private String id;
        private String fileName;
        private boolean loaded;
        private Object parent;
        private Object exports;

        @Override
        public String getClassName() {
            return CLASS_NAME;
        }

        public void setParentScope(Scriptable s) {
            this.parentScope = s;
        }

        public void setRunner(ScriptRunner runner) {
            this.runner = runner;
        }

        public void setFile(File f) {
            this.file = f;
        }

        public void setFileName(String n) {
            this.fileName = n;
        }

        public void bindVariables(Context cx, Scriptable scope, Scriptable target)
        {
            // Create a new object of the "module" class
            scope.put("module", scope, target);

            // Bind the global "require" method to the static method above
            Method require = Utils.findMethod(Module.class, "globalRequire");
            scope.put("require", scope,
                      new FunctionObject("require", require, scope));

            // Create an "exports" object and bind to both the new object and the global scope
            Scriptable exportsObj = cx.newObject(scope);
            scope.put("exports", target, exportsObj);
            scope.put("exports", scope, exportsObj);
        }

        @JSFunction
        public static Object require(Context cx, Scriptable thisObj, Object[] args, Function func)
        {
            // TODO caching of loaded modules!
            if (args.length != 1) {
                throw new EvaluatorException("Invalid arguments to 'require'");
            }
            String name = (String)Context.jsToJava(args[0], String.class);
            ModuleImpl mod = (ModuleImpl)thisObj;
            if (log.isDebugEnabled()) {
                log.debug("require({})...", name);
                log.debug("  Parent scope {}", System.identityHashCode(mod.parentScope));
            }

            Object cached = mod.runner.getModuleCache().get(name);
            if (cached != null) {
                if (log.isDebugEnabled()) {
                    log.debug("Returning cached {}", System.identityHashCode(cached));
                }
                return cached;
            }

            // Now search for the module, first as a native Java object, then elsewhere
            NodeModule nativeMod = mod.runner.getEnvironment().getRegistry().get(name);
            if (nativeMod != null) {
                // Load something that's built in
                try {
                    log.debug("Registering {} from class {}",
                              name, nativeMod.getClass().getName());
                    return mod.runner.registerModule(name, cx, mod.parentScope);

                } catch (InvocationTargetException e) {
                    throw new WrappedException(e);
                } catch (IllegalAccessException e) {
                    throw new WrappedException(e);
                } catch (InstantiationException e) {
                    throw new WrappedException(e);
                }
            }

            // Set up a new scope in which to run the new module
            Scriptable newScope = cx.newObject(mod.parentScope);
            newScope.setPrototype(mod.parentScope);
            newScope.setParentScope(null);

            // Register a new "module" object in the new scope for running the script
            ModuleImpl newMod = (ModuleImpl)cx.newObject(newScope, CLASS_NAME);
            newMod.setRunner(mod.runner);
            newMod.setParent(mod);
            newMod.setId(name);
            newMod.setParentScope(mod.parentScope);
            newMod.bindVariables(cx, newScope, newMod);

            File modFile = null;
            String resourceMod = mod.runner.getEnvironment().getRegistry().getResource(name);
            if (resourceMod == null) {
                // Else, search for the file
                // TODO package.json
                // TODO node_modules
                File search = (mod.file == null) ? new File(".") : mod.file.getParentFile();
                modFile = locateFile(name, search);
                if (modFile == null) {
                    throw new EvaluatorException("Cannot load module \"" + name + '\"');
                }
            }

            if (log.isDebugEnabled()) {
                log.debug("Executing in scope {} with exports {}",
                          System.identityHashCode(newScope),
                          System.identityHashCode(newScope.get("exports", newScope)));
            }
            if (resourceMod == null) {
                log.debug("Executing {}", modFile.getPath());
                newMod.setFile(modFile);
                evaluateFile(cx, newScope, modFile, name);
            } else {
                log.debug("Executing resource {} from classpath", resourceMod);
                newMod.setFileName(resourceMod);
                evaluateResource(cx, newScope, resourceMod, name);
            }
            newMod.setLoaded(true);

            if (log.isDebugEnabled()) {
                log.debug("Done executing in scope {} with exports {}",
                          System.identityHashCode(newScope),
                          System.identityHashCode(newScope.get("exports", newScope)));
            }
            Object exports = newScope.get("exports", newScope);
            mod.runner.getModuleCache().put(name, exports);
            return exports;
        }

        private static Object evaluateFile(Context cx, Scriptable scope, File f, String name)
        {
            try {
                FileInputStream fis = new FileInputStream(f);
                try {
                    InputStreamReader rdr = new InputStreamReader(fis, Utils.UTF8);
                    Object ret = cx.evaluateReader(scope, rdr, name, 1, null);
                    rdr.close();
                    return ret;
                } finally {
                    fis.close();
                }
            } catch (IOException ioe) {
                throw new WrappedException(ioe);
            }
        }

        private static Object evaluateResource(Context cx, Scriptable scope, String path, String name)
        {
            try {
                Reader rdr = Utils.getResource(path);
                try {
                    Object ret = cx.evaluateReader(scope, rdr, name, 1, null);
                    return ret;
                } finally {
                    rdr.close();
                }
            } catch (IOException ioe) {
                throw new WrappedException(ioe);
            }
        }

        private static File locateFile(String name, File dir)
        {
            File f;
            if (name.startsWith("/")) {
                f = new File(name);
            } else if (name.startsWith("./") || name.startsWith("../")) {
                f = new File(dir, name);
            } else {
                return null;
            }

            log.debug("Looking for {} in {}", name, f);
            if (f.exists() && f.isFile()) {
                return f;
            }
            f = new File(f.getPath() + ".js");
            log.debug("Looking for {} in {}", name, f);
            if (f.exists() && f.isFile()) {
                return f;
            }
            return null;
        }

        @JSGetter("filename")
        public String getFileName()
        {
            if (file == null) {
                return fileName;
            } else {
                return file.getPath();
            }
        }

        @JSGetter("id")
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        @JSGetter("loaded")
        public boolean getLoaded() {
            return loaded;
        }

        public void setLoaded(boolean l) {
            this.loaded = l;
        }

        @JSGetter("parent")
        public Object getParent() {
            return parent;
        }

        public void setParent(ModuleImpl p) {
            this.parent = p;
        }

        @JSGetter("exports")
        public Object getExports() {
            return exports;
        }

        @JSSetter("exports")
        public void setExports(Object e) {
            this.exports = e;
        }

        // TODO "children"?
    }
}