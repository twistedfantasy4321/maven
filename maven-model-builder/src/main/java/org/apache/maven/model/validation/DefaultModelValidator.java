package org.apache.maven.model.validation;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.model.Build;
import org.apache.maven.model.BuildBase;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.DistributionManagement;
import org.apache.maven.model.InputLocation;
import org.apache.maven.model.InputLocationTracker;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.model.Profile;
import org.apache.maven.model.ReportPlugin;
import org.apache.maven.model.Reporting;
import org.apache.maven.model.Repository;
import org.apache.maven.model.Resource;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelProblem.Severity;
import org.apache.maven.model.building.ModelProblemCollector;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.util.StringUtils;

/**
 * @author <a href="mailto:trygvis@inamo.no">Trygve Laugst&oslash;l</a>
 * @version $Id$
 */
@Component( role = ModelValidator.class )
public class DefaultModelValidator
    implements ModelValidator
{

    private static final String ID_REGEX = "[A-Za-z0-9_\\-.]+";

    public void validateRawModel( Model model, ModelBuildingRequest request, ModelProblemCollector problems )
    {
        Parent parent = model.getParent();
        if ( parent != null )
        {
            validateStringNotEmpty( "parent.groupId", problems, Severity.FATAL, parent.getGroupId(), parent );

            validateStringNotEmpty( "parent.artifactId", problems, Severity.FATAL, parent.getArtifactId(), parent );

            validateStringNotEmpty( "parent.version", problems, Severity.FATAL, parent.getVersion(), parent );

            if ( equals( parent.getGroupId(), model.getGroupId() )
                && equals( parent.getArtifactId(), model.getArtifactId() ) )
            {
                addViolation( problems, Severity.FATAL, "parent.artifactId", null, "must be changed"
                    + ", the parent element cannot have the same groupId:artifactId as the project.", parent );
            }
        }

        if ( request.getValidationLevel() >= ModelBuildingRequest.VALIDATION_LEVEL_MAVEN_2_0 )
        {
            Severity errOn30 = getSeverity( request, ModelBuildingRequest.VALIDATION_LEVEL_MAVEN_3_0 );

            validateEnum( "modelVersion", problems, Severity.ERROR, model.getModelVersion(), null, model, "4.0.0" );
            validateStringNoExpression( "groupId", problems, Severity.WARNING, model.getGroupId(), model );
            validateStringNoExpression( "artifactId", problems, Severity.WARNING, model.getArtifactId(), model );
            validateStringNoExpression( "version", problems, Severity.WARNING, model.getVersion(), model );

            validateRawDependencies( problems, model.getDependencies(), "dependencies.dependency", request );

            if ( model.getDependencyManagement() != null )
            {
                validateRawDependencies( problems, model.getDependencyManagement().getDependencies(),
                                      "dependencyManagement.dependencies.dependency", request );
            }

            validateRepositories( problems, model.getRepositories(), "repositories.repository", request );

            validateRepositories( problems, model.getPluginRepositories(), "pluginRepositories.pluginRepository",
                                  request );

            Build build = model.getBuild();
            if ( build != null )
            {
                validateRawPlugins( problems, build.getPlugins(), "build.plugins.plugin", request );

                PluginManagement mngt = build.getPluginManagement();
                if ( mngt != null )
                {
                    validateRawPlugins( problems, mngt.getPlugins(), "build.pluginManagement.plugins.plugin",
                                        request );
                }
            }

            Set<String> profileIds = new HashSet<String>();

            for ( Profile profile : model.getProfiles() )
            {
                String prefix = "profiles.profile[" + profile.getId() + "]";

                if ( !profileIds.add( profile.getId() ) )
                {
                    addViolation( problems, errOn30, "profiles.profile.id", null,
                                  "must be unique but found duplicate profile with id " + profile.getId(), profile );
                }

                validateRawDependencies( problems, profile.getDependencies(), prefix + ".dependencies.dependency",
                                         request );

                if ( profile.getDependencyManagement() != null )
                {
                    validateRawDependencies( problems, profile.getDependencyManagement().getDependencies(), prefix
                        + ".dependencyManagement.dependencies.dependency", request );
                }

                validateRepositories( problems, profile.getRepositories(), prefix + ".repositories.repository",
                                      request );

                validateRepositories( problems, profile.getPluginRepositories(), prefix
                    + ".pluginRepositories.pluginRepository", request );

                BuildBase buildBase = profile.getBuild();
                if ( buildBase != null )
                {
                    validateRawPlugins( problems, buildBase.getPlugins(), prefix + ".plugins.plugin", request );

                    PluginManagement mngt = buildBase.getPluginManagement();
                    if ( mngt != null )
                    {
                        validateRawPlugins( problems, mngt.getPlugins(), prefix + ".pluginManagement.plugins.plugin",
                                            request );
                    }
                }
            }
        }
    }

    private void validateRawPlugins( ModelProblemCollector problems, List<Plugin> plugins, String prefix,
                                     ModelBuildingRequest request )
    {
        Severity errOn31 = getSeverity( request, ModelBuildingRequest.VALIDATION_LEVEL_MAVEN_3_1 );

        Map<String, Plugin> index = new HashMap<String, Plugin>();

        for ( Plugin plugin : plugins )
        {
            String key = plugin.getKey();

            Plugin existing = index.get( key );

            if ( existing != null )
            {
                addViolation( problems, errOn31, prefix + ".(groupId:artifactId)", null,
                              "must be unique but found duplicate declaration of plugin " + key, plugin );
            }
            else
            {
                index.put( key, plugin );
            }

            Set<String> executionIds = new HashSet<String>();

            for ( PluginExecution exec : plugin.getExecutions() )
            {
                if ( !executionIds.add( exec.getId() ) )
                {
                    addViolation( problems, Severity.ERROR, prefix + "[" + plugin.getKey()
                        + "].executions.execution.id", null, "must be unique but found duplicate execution with id "
                        + exec.getId(), exec );
                }
            }
        }
    }

    public void validateEffectiveModel( Model model, ModelBuildingRequest request, ModelProblemCollector problems )
    {
        validateStringNotEmpty( "modelVersion", problems, Severity.ERROR, model.getModelVersion(), model );

        validateId( "groupId", problems, model.getGroupId(), model );

        validateId( "artifactId", problems, model.getArtifactId(), model );

        validateStringNotEmpty( "packaging", problems, Severity.ERROR, model.getPackaging(), model );

        if ( !model.getModules().isEmpty() )
        {
            if ( !"pom".equals( model.getPackaging() ) )
            {
                addViolation( problems, Severity.ERROR, "packaging", null, "with value '" + model.getPackaging()
                    + "' is invalid. Aggregator projects " + "require 'pom' as packaging.", model );
            }

            for ( int i = 0, n = model.getModules().size(); i < n; i++ )
            {
                String module = model.getModules().get( i );
                if ( StringUtils.isBlank( module ) )
                {
                    addViolation( problems, Severity.WARNING, "modules.module[" + i + "]", null,
                                  "has been specified without a path to the project directory.",
                                  model.getLocation( "modules" ) );
                }
            }
        }

        validateStringNotEmpty( "version", problems, Severity.ERROR, model.getVersion(), model );

        Severity errOn30 = getSeverity( request, ModelBuildingRequest.VALIDATION_LEVEL_MAVEN_3_0 );

        validateEffectiveDependencies( problems, model.getDependencies(), false, request );

        DependencyManagement mgmt = model.getDependencyManagement();
        if ( mgmt != null )
        {
            validateEffectiveDependencies( problems, mgmt.getDependencies(), true, request );
        }

        if ( request.getValidationLevel() >= ModelBuildingRequest.VALIDATION_LEVEL_MAVEN_2_0 )
        {
            Set<String> modules = new HashSet<String>();
            for ( int i = 0, n = model.getModules().size(); i < n; i++ )
            {
                String module = model.getModules().get( i );
                if ( !modules.add( module ) )
                {
                    addViolation( problems, Severity.ERROR, "modules.module[" + i + "]", null,
                                  "specifies duplicate child module " + module, model.getLocation( "modules" ) );
                }
            }

            Severity errOn31 = getSeverity( request, ModelBuildingRequest.VALIDATION_LEVEL_MAVEN_3_1 );

            Build build = model.getBuild();
            if ( build != null )
            {
                for ( Plugin p : build.getPlugins() )
                {
                    validateStringNotEmpty( "build.plugins.plugin.artifactId", problems, Severity.ERROR,
                                            p.getArtifactId(), p );

                    validateStringNotEmpty( "build.plugins.plugin.groupId", problems, Severity.ERROR, p.getGroupId(),
                                            p );

                    validatePluginVersion( "build.plugins.plugin.version", problems, p.getVersion(), p.getKey(), p,
                                           request );

                    validateBoolean( "build.plugins.plugin.inherited", problems, errOn30, p.getInherited(), p.getKey(),
                                     p );

                    validateBoolean( "build.plugins.plugin.extensions", problems, errOn30, p.getExtensions(),
                                     p.getKey(), p );

                    validateEffectivePluginDependencies( problems, p, request );
                }

                validateResources( problems, build.getResources(), "build.resources.resource", request );

                validateResources( problems, build.getTestResources(), "build.testResources.testResource", request );
            }

            Reporting reporting = model.getReporting();
            if ( reporting != null )
            {
                for ( ReportPlugin p : reporting.getPlugins() )
                {
                    validateStringNotEmpty( "reporting.plugins.plugin.artifactId", problems, Severity.ERROR,
                                            p.getArtifactId(), p );

                    validateStringNotEmpty( "reporting.plugins.plugin.groupId", problems, Severity.ERROR,
                                            p.getGroupId(), p );

                    validateStringNotEmpty( "reporting.plugins.plugin.version", problems, errOn31, p.getVersion(),
                                            p.getKey(), p );
                }
            }

            for ( Repository repository : model.getRepositories() )
            {
                validateRepository( problems, repository, "repositories.repository", request );
            }

            for ( Repository repository : model.getPluginRepositories() )
            {
                validateRepository( problems, repository, "pluginRepositories.pluginRepository", request );
            }

            DistributionManagement distMgmt = model.getDistributionManagement();
            if ( distMgmt != null )
            {
                if ( distMgmt.getStatus() != null )
                {
                    addViolation( problems, Severity.ERROR, "distributionManagement.status", null,
                                  "must not be specified.", distMgmt );
                }

                validateRepository( problems, distMgmt.getRepository(), "distributionManagement.repository", request );
                validateRepository( problems, distMgmt.getSnapshotRepository(),
                                    "distributionManagement.snapshotRepository", request );
            }
        }
    }

    private void validateRawDependencies( ModelProblemCollector problems, List<Dependency> dependencies, String prefix,
                                       ModelBuildingRequest request )
    {
        Severity errOn30 = getSeverity( request, ModelBuildingRequest.VALIDATION_LEVEL_MAVEN_3_0 );
        Severity errOn31 = getSeverity( request, ModelBuildingRequest.VALIDATION_LEVEL_MAVEN_3_1 );

        Map<String, Dependency> index = new HashMap<String, Dependency>();

        for ( Dependency dependency : dependencies )
        {
            String key = dependency.getManagementKey();

            if ( "pom".equals( dependency.getType() ) && "import".equals( dependency.getScope() )
                && StringUtils.isNotEmpty( dependency.getClassifier() ) )
            {
                addViolation( problems, errOn30, prefix + ".classifier", key,
                              "must be empty, imported POM cannot have a classifier.", dependency );
            }
            else if ( "system".equals( dependency.getScope() ) )
            {
                String sysPath = dependency.getSystemPath();
                if ( StringUtils.isNotEmpty( sysPath ) && !hasExpression( sysPath ) )
                {
                    addViolation( problems, Severity.WARNING, prefix + ".systemPath", key,
                                  "should use a variable instead of a hard-coded path " + sysPath, dependency );
                }
            }

            Dependency existing = index.get( key );

            if ( existing != null )
            {
                String msg;
                if ( equals( existing.getVersion(), dependency.getVersion() ) )
                {
                    msg =
                        "duplicate declaration of version "
                            + StringUtils.defaultString( dependency.getVersion(), "(?)" );
                }
                else
                {
                    msg =
                        "version " + StringUtils.defaultString( existing.getVersion(), "(?)" ) + " vs "
                            + StringUtils.defaultString( dependency.getVersion(), "(?)" );
                }

                addViolation( problems, errOn31, prefix + ".(groupId:artifactId:type:classifier)", null,
                              "must be unique: " + key + " -> " + msg, dependency );
            }
            else
            {
                index.put( key, dependency );
            }
        }
    }

    private void validateEffectiveDependencies( ModelProblemCollector problems, List<Dependency> dependencies,
                                                boolean management, ModelBuildingRequest request )
    {
        Severity errOn30 = getSeverity( request, ModelBuildingRequest.VALIDATION_LEVEL_MAVEN_3_0 );

        String prefix = management ? "dependencyManagement.dependencies.dependency." : "dependencies.dependency.";

        for ( Dependency d : dependencies )
        {
            validateEffectiveDependency( problems, d, management, prefix, request );

            if ( request.getValidationLevel() >= ModelBuildingRequest.VALIDATION_LEVEL_MAVEN_2_0 )
            {
                validateBoolean( prefix + "optional", problems, errOn30, d.getOptional(), d.getManagementKey(), d );

                if ( !management )
                {
                    validateVersion( prefix + "version", problems, errOn30, d.getVersion(), d.getManagementKey(), d );

                    /*
                     * TODO: Extensions like Flex Mojos use custom scopes like "merged", "internal", "external", etc.
                     * In order to don't break backward-compat with those, only warn but don't error out.
                     */
                    validateEnum( prefix + "scope", problems, Severity.WARNING, d.getScope(), d.getManagementKey(), d,
                                  "provided", "compile", "runtime", "test", "system" );
                }
            }
        }
    }

    private void validateEffectivePluginDependencies( ModelProblemCollector problems, Plugin plugin,
                                                      ModelBuildingRequest request )
    {
        List<Dependency> dependencies = plugin.getDependencies();

        if ( !dependencies.isEmpty() )
        {
            String prefix = "build.plugins.plugin[" + plugin.getKey() + "].dependencies.dependency.";

            Severity errOn30 = getSeverity( request, ModelBuildingRequest.VALIDATION_LEVEL_MAVEN_3_0 );

            for ( Dependency d : dependencies )
            {
                validateEffectiveDependency( problems, d, false, prefix, request );

                validateVersion( prefix + "version", problems, errOn30, d.getVersion(), d.getManagementKey(), d );

                validateEnum( prefix + "scope", problems, errOn30, d.getScope(), d.getManagementKey(), d, "compile",
                              "runtime", "system" );
            }
        }
    }

    private void validateEffectiveDependency( ModelProblemCollector problems, Dependency d, boolean management,
                                              String prefix, ModelBuildingRequest request )
    {
        validateId( prefix + "artifactId", problems, d.getArtifactId(), d.getManagementKey(), d );

        validateId( prefix + "groupId", problems, d.getGroupId(), d.getManagementKey(), d );

        if ( !management )
        {
            validateStringNotEmpty( prefix + "type", problems, Severity.ERROR, d.getType(), d.getManagementKey(), d );

            validateStringNotEmpty( prefix + "version", problems, Severity.ERROR, d.getVersion(), d.getManagementKey(),
                                    d );
        }

        if ( "system".equals( d.getScope() ) )
        {
            String systemPath = d.getSystemPath();

            if ( StringUtils.isEmpty( systemPath ) )
            {
                addViolation( problems, Severity.ERROR, prefix + "systemPath", d.getManagementKey(), "is missing.",
                              d );
            }
            else
            {
                File sysFile = new File( systemPath );
                if ( !sysFile.isAbsolute() )
                {
                    addViolation( problems, Severity.ERROR, prefix + "systemPath", d.getManagementKey(),
                                  "must specify an absolute path but is " + systemPath, d );
                }
                else if ( !sysFile.isFile() )
                {
                    String msg = "refers to a non-existing file " + sysFile.getAbsolutePath();
                    systemPath = systemPath.replace( '/', File.separatorChar ).replace( '\\', File.separatorChar );
                    String jdkHome =
                        request.getSystemProperties().getProperty( "java.home", "" ) + File.separator + "..";
                    if ( systemPath.startsWith( jdkHome ) )
                    {
                        msg += ". Please verify that you run Maven using a JDK and not just a JRE.";
                    }
                    addViolation( problems, Severity.WARNING, prefix + "systemPath", d.getManagementKey(), msg, d );
                }
            }
        }
        else if ( StringUtils.isNotEmpty( d.getSystemPath() ) )
        {
            addViolation( problems, Severity.ERROR, prefix + "systemPath", d.getManagementKey(), "must be omitted."
                + " This field may only be specified for a dependency with system scope.", d );
        }
    }

    private void validateRepositories( ModelProblemCollector problems, List<Repository> repositories, String prefix,
                                       ModelBuildingRequest request )
    {
        Map<String, Repository> index = new HashMap<String, Repository>();

        for ( Repository repository : repositories )
        {
            validateStringNotEmpty( prefix + ".id", problems, Severity.ERROR, repository.getId(), repository );

            validateStringNotEmpty( prefix + "[" + repository.getId() + "].url", problems, Severity.ERROR,
                                    repository.getUrl(), repository );

            String key = repository.getId();

            Repository existing = index.get( key );

            if ( existing != null )
            {
                Severity errOn30 = getSeverity( request, ModelBuildingRequest.VALIDATION_LEVEL_MAVEN_3_0 );

                addViolation( problems, errOn30, prefix + ".id", null, "must be unique: " + repository.getId() + " -> "
                    + existing.getUrl() + " vs " + repository.getUrl(), repository );
            }
            else
            {
                index.put( key, repository );
            }
        }
    }

    private void validateRepository( ModelProblemCollector problems, Repository repository, String prefix,
                                     ModelBuildingRequest request )
    {
        if ( repository != null )
        {
            if ( "local".equals( repository.getId() ) )
            {
                Severity errOn31 = getSeverity( request, ModelBuildingRequest.VALIDATION_LEVEL_MAVEN_3_1 );
                addViolation( problems, errOn31, prefix + ".id", null, "must not be 'local'"
                    + ", this identifier is reserved for the local repository"
                    + ", using it for other repositories will corrupt your repository metadata.", repository );
            }
            if ( "legacy".equals( repository.getLayout() ) )
            {
                addViolation( problems, Severity.WARNING, prefix + ".layout", repository.getId(),
                              "uses the unsupported value 'legacy', artifact resolution might fail.", repository );
            }
        }
    }

    private void validateResources( ModelProblemCollector problems, List<Resource> resources, String prefix,
                                    ModelBuildingRequest request )
    {
        Severity errOn30 = getSeverity( request, ModelBuildingRequest.VALIDATION_LEVEL_MAVEN_3_0 );

        for ( Resource resource : resources )
        {
            validateStringNotEmpty( prefix + ".directory", problems, Severity.ERROR, resource.getDirectory(),
                                    resource );

            validateBoolean( prefix + ".filtering", problems, errOn30, resource.getFiltering(),
                             resource.getDirectory(), resource );
        }
    }

    // ----------------------------------------------------------------------
    // Field validation
    // ----------------------------------------------------------------------

    private boolean validateId( String fieldName, ModelProblemCollector problems, String id,
                                InputLocationTracker tracker )
    {
        return validateId( fieldName, problems, id, null, tracker );
    }

    private boolean validateId( String fieldName, ModelProblemCollector problems, String id, String sourceHint,
                                InputLocationTracker tracker )
    {
        if ( !validateStringNotEmpty( fieldName, problems, Severity.ERROR, id, sourceHint, tracker ) )
        {
            return false;
        }
        else
        {
            boolean match = id.matches( ID_REGEX );
            if ( !match )
            {
                addViolation( problems, Severity.ERROR, fieldName, sourceHint, "with value '" + id
                    + "' does not match a valid id pattern.", tracker );
            }
            return match;
        }
    }

    private boolean validateStringNoExpression( String fieldName, ModelProblemCollector problems, Severity severity,
                                                String string, InputLocationTracker tracker )
    {
        if ( !hasExpression( string ) )
        {
            return true;
        }

        addViolation( problems, severity, fieldName, null, "contains an expression but should be a constant.",
                      tracker );

        return false;
    }

    private boolean hasExpression( String value )
    {
        return value != null && value.indexOf( "${" ) >= 0;
    }

    private boolean validateStringNotEmpty( String fieldName, ModelProblemCollector problems, Severity severity,
                                            String string, InputLocationTracker tracker )
    {
        return validateStringNotEmpty( fieldName, problems, severity, string, null, tracker );
    }

    /**
     * Asserts:
     * <p/>
     * <ul>
     * <li><code>string != null</code>
     * <li><code>string.length > 0</code>
     * </ul>
     */
    private boolean validateStringNotEmpty( String fieldName, ModelProblemCollector problems, Severity severity,
                                            String string, String sourceHint, InputLocationTracker tracker )
    {
        if ( !validateNotNull( fieldName, problems, severity, string, sourceHint, tracker ) )
        {
            return false;
        }

        if ( string.length() > 0 )
        {
            return true;
        }

        addViolation( problems, severity, fieldName, sourceHint, "is missing.", tracker );

        return false;
    }

    /**
     * Asserts:
     * <p/>
     * <ul>
     * <li><code>string != null</code>
     * </ul>
     */
    private boolean validateNotNull( String fieldName, ModelProblemCollector problems, Severity severity,
                                     Object object, String sourceHint, InputLocationTracker tracker )
    {
        if ( object != null )
        {
            return true;
        }

        addViolation( problems, severity, fieldName, sourceHint, "is missing.", tracker );

        return false;
    }

    private boolean validateBoolean( String fieldName, ModelProblemCollector problems, Severity severity,
                                     String string, String sourceHint, InputLocationTracker tracker )
    {
        if ( string == null || string.length() <= 0 )
        {
            return true;
        }

        if ( "true".equalsIgnoreCase( string ) || "false".equalsIgnoreCase( string ) )
        {
            return true;
        }

        addViolation( problems, severity, fieldName, sourceHint, "must be 'true' or 'false' but is '" + string + "'.",
                      tracker );

        return false;
    }

    private boolean validateEnum( String fieldName, ModelProblemCollector problems, Severity severity, String string,
                                  String sourceHint, InputLocationTracker tracker, String... validValues )
    {
        if ( string == null || string.length() <= 0 )
        {
            return true;
        }

        List<String> values = Arrays.asList( validValues );

        if ( values.contains( string ) )
        {
            return true;
        }

        addViolation( problems, severity, fieldName, sourceHint, "must be one of " + values + " but is '" + string
            + "'.", tracker );

        return false;
    }

    private boolean validateVersion( String fieldName, ModelProblemCollector problems, Severity severity,
                                     String string, String sourceHint, InputLocationTracker tracker )
    {
        if ( string == null || string.length() <= 0 )
        {
            return true;
        }

        if ( !hasExpression( string ) )
        {
            return true;
        }

        addViolation( problems, severity, fieldName, sourceHint, "must be a valid version but is '" + string + "'.",
                      tracker );

        return false;
    }

    private boolean validatePluginVersion( String fieldName, ModelProblemCollector problems, String string,
                                           String sourceHint, InputLocationTracker tracker,
                                           ModelBuildingRequest request )
    {
        Severity errOn30 = getSeverity( request, ModelBuildingRequest.VALIDATION_LEVEL_MAVEN_3_0 );

        if ( string == null )
        {
            // NOTE: The check for missing plugin versions is handled directly by the model builder
            return true;
        }

        if ( string.length() > 0 && !hasExpression( string ) && !"RELEASE".equals( string )
            && !"LATEST".equals( string ) )
        {
            return true;
        }

        addViolation( problems, errOn30, fieldName, sourceHint, "must be a valid version but is '" + string + "'.",
                      tracker );

        return false;
    }

    private static void addViolation( ModelProblemCollector problems, Severity severity, String fieldName,
                                      String sourceHint, String message, InputLocationTracker tracker )
    {
        StringBuilder buffer = new StringBuilder( 256 );
        buffer.append( '\'' ).append( fieldName ).append( '\'' );

        if ( sourceHint != null )
        {
            buffer.append( " for " ).append( sourceHint );
        }

        buffer.append( ' ' ).append( message );

        problems.add( severity, buffer.toString(), getLocation( fieldName, tracker ), null );
    }

    private static InputLocation getLocation( String fieldName, InputLocationTracker tracker )
    {
        InputLocation location = null;

        if ( tracker != null )
        {
            if ( fieldName != null )
            {
                Object key = fieldName;

                int idx = fieldName.lastIndexOf( '.' );
                if ( idx >= 0 )
                {
                    key = fieldName = fieldName.substring( idx + 1 );
                }

                if ( fieldName.endsWith( "]" ) )
                {
                    key = fieldName.substring( fieldName.lastIndexOf( '[' ) + 1, fieldName.length() - 1 );
                    try
                    {
                        key = Integer.valueOf( key.toString() );
                    }
                    catch ( NumberFormatException e )
                    {
                        // use key as is
                    }
                }

                location = tracker.getLocation( key );
            }

            if ( location == null )
            {
                location = tracker.getLocation( "" );
            }
        }

        return location;
    }

    private static boolean equals( String s1, String s2 )
    {
        return StringUtils.clean( s1 ).equals( StringUtils.clean( s2 ) );
    }

    private static Severity getSeverity( ModelBuildingRequest request, int errorThreshold )
    {
        return getSeverity( request.getValidationLevel(), errorThreshold );
    }

    private static Severity getSeverity( int validationLevel, int errorThreshold )
    {
        if ( validationLevel < errorThreshold )
        {
            return Severity.WARNING;
        }
        else
        {
            return Severity.ERROR;
        }
    }

}
