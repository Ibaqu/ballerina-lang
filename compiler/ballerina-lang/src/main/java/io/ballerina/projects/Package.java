package io.ballerina.projects;

import io.ballerina.projects.internal.ManifestBuilder;
import io.ballerina.projects.internal.model.CompilerPluginDescriptor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * {@code Package} represents a Ballerina Package.
 *
 * @since 2.0.0
 */
public class Package {
    private final Project project;
    private final PackageContext packageContext;
    private final Map<ModuleId, Module> moduleMap;
    private final Function<ModuleId, Module> populateModuleFunc;
    // Following are not final since they will be lazy loaded
    private Optional<PackageMd> packageMd = null;
    private Optional<BallerinaToml> ballerinaToml = null;
    private Optional<DependenciesToml> dependenciesToml = null;
    private Optional<CloudToml> cloudToml = null;
    private Optional<CompilerPluginToml> compilerPluginToml = null;

    private Package(PackageContext packageContext, Project project) {
        this.packageContext = packageContext;
        this.project = project;
        this.moduleMap = new ConcurrentHashMap<>();
        this.populateModuleFunc = moduleId -> Module.from(
                this.packageContext.moduleContext(moduleId), this);
    }

    static Package from(Project project, PackageConfig packageConfig, CompilationOptions compilationOptions) {
        // TODO create package context here by giving the package config
        // do the same for modules and documents
        // il. package context creates modules contexts and modules context create document contexts

        // contexts need to hold onto the configs. Should we decouple config from tree information as follows.
        // package config has the tree information like modules.
        PackageContext packageContext = PackageContext.from(project, packageConfig, compilationOptions);
        return new Package(packageContext, project);
    }

    PackageContext packageContext() {
        return this.packageContext;
    }

    public Project project() {
        return this.project;
    }

    public PackageId packageId() {
        return this.packageContext.packageId();
    }

    public PackageName packageName() {
        return packageContext.packageName();
    }

    public PackageOrg packageOrg() {
        return packageContext.packageOrg();
    }

    public PackageVersion packageVersion() {
        return packageContext.packageVersion();
    }

    public PackageDescriptor descriptor() {
        return packageContext.descriptor();
    }

    public Optional<CompilerPluginDescriptor> compilerPluginDescriptor() {
        return packageContext.compilerPluginDescriptor();
    }

    public PackageManifest manifest() {
        return packageContext.manifest();
    }

    public Collection<ModuleId> moduleIds() {
        return this.packageContext.moduleIds();
    }

    public Iterable<Module> modules() {
        List<Module> moduleList = new ArrayList<>();
        for (ModuleId moduleId : this.packageContext.moduleIds()) {
            moduleList.add(module(moduleId));
        }
        return new ModuleIterable(moduleList);
    }

    public Module module(ModuleId moduleId) {
        // TODO Should we throw an error if the moduleId is not present
        return this.moduleMap.computeIfAbsent(moduleId, this.populateModuleFunc);
    }

    public Module module(ModuleName moduleName) {
        for (Module module : this.moduleMap.values()) {
            if (module.moduleName().equals(moduleName)) {
                return module;
            }
        }

        ModuleContext moduleContext = this.packageContext.moduleContext(moduleName);
        if (moduleContext != null) {
            return module(moduleContext.moduleId());
        }

        return null;
    }

    public boolean containsModule(ModuleId moduleId) {
        return this.moduleMap.containsKey(moduleId);
    }

    public Module getDefaultModule() {
        return module(this.packageContext.defaultModuleContext().moduleId());
    }

    public PackageCompilation getCompilation() {
        return this.packageContext.getPackageCompilation();
    }

    public PackageResolution getResolution() {
        return this.packageContext.getResolution();
    }

    public DependencyGraph<ModuleId> moduleDependencyGraph() {
        // Each Package should know the packages that it depends on and packages that depends on it
        // Each Module should know the modules that it depends on and modules that depends on it
        return this.packageContext.moduleDependencyGraph();
    }

    public Collection<PackageDependency> packageDependencies() {
        return packageContext.packageDependencies();
    }

    public CompilationOptions compilationOptions() {
        return packageContext.compilationOptions();
    }

    public Optional<BallerinaToml> ballerinaToml() {
        if (null == this.ballerinaToml) {
            this.ballerinaToml = this.packageContext.ballerinaTomlContext().map(c ->
                    BallerinaToml.from(c, this)
            );
        }
        return this.ballerinaToml;
    }

    public Optional<DependenciesToml> dependenciesToml() {
        if (null == this.dependenciesToml) {
            this.dependenciesToml = this.packageContext.dependenciesTomlContext().map(c ->
                    DependenciesToml.from(c, this)
            );
        }
        return this.dependenciesToml;
    }

    public Optional<CloudToml> cloudToml() {
        if (null == this.cloudToml) {
            this.cloudToml = this.packageContext.cloudTomlContext().map(c ->
                    CloudToml.from(c, this));
        }
        return this.cloudToml;
    }

    public Optional<CompilerPluginToml> compilerPluginToml() {
        if (null == this.compilerPluginToml) {
            this.compilerPluginToml = this.packageContext.compilerPluginTomlContext()
                    .map(c -> CompilerPluginToml.from(c, this));
        }
        return this.compilerPluginToml;
    }

    public Optional<PackageMd> packageMd() {
        if (null == this.packageMd) {
            this.packageMd = this.packageContext.packageMdContext().map(c ->
                    PackageMd.from(c, this)
            );
        }
        return this.packageMd;
    }

    /**
     * Returns an instance of the Package.Modifier.
     *
     * @return module modifier
     */
    public Modifier modify() {
        return new Modifier(this);
    }

    private static class ModuleIterable implements Iterable {

        private final Collection<Module> moduleList;

        public ModuleIterable(Collection<Module> moduleList) {
            this.moduleList = moduleList;
        }

        @Override
        public Iterator<Module> iterator() {
            return this.moduleList.iterator();
        }

        @Override
        public Spliterator spliterator() {
            return this.moduleList.spliterator();
        }
    }

    /**
     * Inner class that handles package modifications.
     */
    public static class Modifier {
        private PackageId packageId;
        private PackageManifest packageManifest;
        private Map<ModuleId, ModuleContext> moduleContextMap;
        private Project project;
        private final DependencyGraph<PackageDescriptor> pkgDependencyGraph;
        private CompilationOptions compilationOptions;
        private TomlDocumentContext ballerinaTomlContext;
        private TomlDocumentContext dependenciesTomlContext;
        private TomlDocumentContext cloudTomlContext;
        private TomlDocumentContext compilerPluginTomlContext;
        private MdDocumentContext packageMdContext;

        public Modifier(Package oldPackage) {
            this.packageId = oldPackage.packageId();
            this.packageManifest = oldPackage.manifest();
            this.moduleContextMap = copyModules(oldPackage);
            this.project = oldPackage.project;
            this.pkgDependencyGraph = oldPackage.packageContext().dependencyGraph();
            this.compilationOptions = oldPackage.compilationOptions();
            this.ballerinaTomlContext = oldPackage.packageContext.ballerinaTomlContext().orElse(null);
            this.dependenciesTomlContext = oldPackage.packageContext.dependenciesTomlContext().orElse(null);
            this.cloudTomlContext = oldPackage.packageContext.cloudTomlContext().orElse(null);
            this.compilerPluginTomlContext = oldPackage.packageContext.compilerPluginTomlContext().orElse(null);
            this.packageMdContext = oldPackage.packageContext.packageMdContext().orElse(null);
        }

        Modifier updateModules(Set<ModuleContext> newModuleContexts) {
            for (ModuleContext newModuleContext : newModuleContexts) {
                this.moduleContextMap.put(newModuleContext.moduleId(), newModuleContext);
            }
            return this;
        }

        /**
         * Adds a new module in a new package that is copied from the existing.
         *
         * @param moduleConfig configuration of the module to add
         * @return Package.Modifier which contains the updated package
         */
        public Modifier addModule(ModuleConfig moduleConfig) {
            ModuleContext newModuleContext = ModuleContext.from(this.project, moduleConfig);
            this.moduleContextMap.put(newModuleContext.moduleId(), newModuleContext);
            return this;
        }

        /**
         * Adds a Dependencies toml.
         *
         * @param documentConfig configuration of the toml document
         * @return Package.Modifier which contains the updated package
         */
        public Modifier addDependenciesToml(DocumentConfig documentConfig) {
            TomlDocumentContext tomlDocumentContext = TomlDocumentContext.from(documentConfig);
            this.dependenciesTomlContext = tomlDocumentContext;
            return this;
        }


        /**
         * Remove Dependencies toml.
         *
         * @return Package.Modifier which contains the updated package
         */
        public Modifier removeDependenciesToml() {
            this.dependenciesTomlContext = null;
            return this;
        }

        /**
         * Adds a Cloud toml.
         *
         * @param documentConfig configuration of the toml document
         * @return Package.Modifier which contains the updated package
         */
        public Modifier addCloudToml(DocumentConfig documentConfig) {
            TomlDocumentContext tomlDocumentContext = TomlDocumentContext.from(documentConfig);
            this.cloudTomlContext = tomlDocumentContext;
            updateManifest();
            return this;
        }

        /**
         * Remove Cloud toml.
         *
         * @return Package.Modifier which contains the updated package
         */
        public Modifier removeCloudToml() {
            this.cloudTomlContext = null;
            return this;
        }

        /**
         * Adds a Compiler plugin toml.
         *
         * @param documentConfig configuration of the toml document
         * @return Package.Modifier which contains the updated package
         */
        public Modifier addCompilerPluginToml(DocumentConfig documentConfig) {
            TomlDocumentContext tomlDocumentContext = TomlDocumentContext.from(documentConfig);
            this.compilerPluginTomlContext = tomlDocumentContext;
            updateManifest();
            return this;
        }

        /**
         * Remove Compiler plugin toml.
         *
         * @return Package.Modifier which contains the updated package
         */
        public Modifier removeCompilerPluginToml() {
            this.compilerPluginTomlContext = null;
            return this;
        }

        /**
         * Adds a package md.
         *
         * @param documentConfig configuration of the toml document
         * @return Package.Modifier which contains the updated package
         */
        public Modifier addPackageMd(DocumentConfig documentConfig) {
            MdDocumentContext tomlDocumentContext = MdDocumentContext.from(documentConfig);
            this.packageMdContext = tomlDocumentContext;
            return this;
        }

        /**
         * Remove package md.
         *
         * @return Package.Modifier which contains the updated package
         */
        public Modifier removePackageMd() {
            this.packageMdContext = null;
            return this;
        }



        Modifier updateBallerinaToml(BallerinaToml ballerinaToml) {
            this.ballerinaTomlContext = ballerinaToml.ballerinaTomlContext();
            updateManifest();
            updateModules();
            return this;
        }

        Modifier updateDependenciesToml(DependenciesToml dependenciesToml) {
            this.dependenciesTomlContext = dependenciesToml.dependenciesTomlContext();
            updateManifest();
            return this;
        }

        Modifier updateCloudToml(CloudToml cloudToml) {
            this.cloudTomlContext = cloudToml.cloudTomlContext();
            return this;
        }

        Modifier updateCompilerPluginToml(CompilerPluginToml compilerPluginToml) {
            this.compilerPluginTomlContext = compilerPluginToml.compilerPluginTomlContext();
            return this;
        }

        Modifier updatePackageMd(MdDocumentContext packageMd) {
            this.packageMdContext = packageMd;
            return this;
        }

        /**
         * Returns the updated package created by a module add/remove/update operation.
         *
         * @return updated package
         */
        public Package apply() {
            return createNewPackage();
        }

        private Map<ModuleId, ModuleContext> copyModules(Package oldPackage) {
            Map<ModuleId, ModuleContext> moduleContextMap = new HashMap<>();
            for (ModuleId moduleId : oldPackage.packageContext.moduleIds()) {
                moduleContextMap.put(moduleId, oldPackage.packageContext.moduleContext(moduleId));
            }
            return moduleContextMap;
        }

        private Package createNewPackage() {
            PackageContext newPackageContext = new PackageContext(this.project, this.packageId, this.packageManifest,
                    this.ballerinaTomlContext, this.dependenciesTomlContext, this.cloudTomlContext,
                    this.compilerPluginTomlContext, this.packageMdContext,  this.compilationOptions,
                    this.moduleContextMap, this.pkgDependencyGraph);
            this.project.setCurrentPackage(new Package(newPackageContext, this.project));
            return this.project.currentPackage();
        }

        private void updateManifest() {
            ManifestBuilder manifestBuilder = ManifestBuilder.from(this.ballerinaTomlContext.tomlDocument(),
                    Optional.ofNullable(this.dependenciesTomlContext).map(d -> d.tomlDocument()).orElse(null),
                    Optional.ofNullable(this.compilerPluginTomlContext).map(d -> d.tomlDocument()).orElse(null),
                    this.project.sourceRoot());
            this.packageManifest = manifestBuilder.packageManifest();
            BuildOptions newBuildOptions;
            if (manifestBuilder.buildOptions() == null) {
                newBuildOptions = new BuildOptionsBuilder().build();
            } else {
                newBuildOptions = manifestBuilder.buildOptions();
            }
            this.project.setBuildOptions(this.project.buildOptions().acceptTheirs(newBuildOptions));
        }

        private void updateModules() {
            Set<ModuleContext> moduleContextSet = new HashSet<>();
            for (Map.Entry<ModuleId, ModuleContext> moduleIdModuleContextEntry : moduleContextMap.entrySet()) {
                ModuleId moduleId = moduleIdModuleContextEntry.getKey();
                ModuleContext oldModuleContext = moduleIdModuleContextEntry.getValue();

                PackageDescriptor packageDescriptor = this.packageManifest.descriptor();
                ModuleName moduleName = ModuleName.from(
                        packageDescriptor.name(), oldModuleContext.moduleName().moduleNamePart());
                ModuleDescriptor moduleDescriptor = ModuleDescriptor.from(moduleName, packageDescriptor);

                Map<DocumentId, DocumentContext> srcDocContextMap = new HashMap<>();
                for (DocumentId documentId : oldModuleContext.srcDocumentIds()) {
                    srcDocContextMap.put(documentId, oldModuleContext.documentContext(documentId));
                }

                Map<DocumentId, DocumentContext> testDocContextMap = new HashMap<>();
                for (DocumentId documentId : oldModuleContext.testSrcDocumentIds()) {
                    testDocContextMap.put(documentId, oldModuleContext.documentContext(documentId));
                }

                moduleContextSet.add(new ModuleContext(this.project, moduleId, moduleDescriptor,
                        oldModuleContext.isDefaultModule(), srcDocContextMap, testDocContextMap,
                        oldModuleContext.moduleMdContext().orElse(null),
                        oldModuleContext.moduleDescDependencies()));
            }
            updateModules(moduleContextSet);
        }
    }
}
