package org.apache.maven.project;

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

import java.util.Date;
import java.util.List;
import java.util.Properties;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.RepositoryCache;
import org.apache.maven.model.Profile;
import org.apache.maven.repository.ArtifactTransferListener;
import org.apache.maven.settings.Mirror;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Server;

public interface ProjectBuildingRequest
{

    ProjectBuildingRequest setOffline( boolean offline );

    boolean isOffline();

    ProjectBuildingRequest setForceUpdate( boolean forceUpdate );

    boolean isForceUpdate();

    ProjectBuildingRequest setRepositoryCache( RepositoryCache repositoryCache );

    RepositoryCache getRepositoryCache();

    ProjectBuildingRequest setLocalRepository( ArtifactRepository localRepository );

    ArtifactRepository getLocalRepository();

    ProjectBuildingRequest setRemoteRepositories( List<ArtifactRepository> remoteRepositories );

    List<ArtifactRepository> getRemoteRepositories();

    ProjectBuildingRequest setPluginArtifactRepositories( List<ArtifactRepository> pluginArtifacgRepositories );

    List<ArtifactRepository> getPluginArtifactRepositories();

    ProjectBuildingRequest setServers( List<Server> servers );

    List<Server> getServers();

    ProjectBuildingRequest setMirrors( List<Mirror> mirrors );

    List<Mirror> getMirrors();

    ProjectBuildingRequest setProxies( List<Proxy> proxies );

    List<Proxy> getProxies();

    /**
     * Sets the system properties to use for interpolation and profile activation. The system properties are collected
     * from the runtime environment like {@link System#getProperties()} and environment variables.
     *
     * @param systemProperties The system properties, may be {@code null}.
     * @return This request, never {@code null}.
     */
    ProjectBuildingRequest setSystemProperties( Properties systemProperties );

    /**
     * Gets the system properties to use for interpolation and profile activation. The system properties are collected
     * from the runtime environment like {@link System#getProperties()} and environment variables.
     *
     * @return The system properties, never {@code null}.
     */
    Properties getSystemProperties();

    /**
     * Sets the user properties to use for interpolation and profile activation. The user properties have been
     * configured directly by the user on his discretion, e.g. via the {@code -Dkey=value} parameter on the command
     * line.
     *
     * @param userProperties The user properties, may be {@code null}.
     * @return This request, never {@code null}.
     */
    ProjectBuildingRequest setUserProperties( Properties userProperties );

    /**
     * Gets the user properties to use for interpolation and profile activation. The user properties have been
     * configured directly by the user on his discretion, e.g. via the {@code -Dkey=value} parameter on the command
     * line.
     *
     * @return The user properties, never {@code null}.
     */
    Properties getUserProperties();

    void setProject( MavenProject mavenProject );

    MavenProject getProject();

    ProjectBuildingRequest setProcessPlugins( boolean processPlugins );

    boolean isProcessPlugins();

    ProjectBuildingRequest setResolveDependencies( boolean resolveDependencies );

    boolean isResolveDependencies();

    /**
     * Controls the level of validation to perform on processed models. By default, models are validated in strict mode.
     *
     * @param validationLevel The level of validation to perform on processed models, e.g.
     *            {@link ModelBuildingRequest#VALIDATION_LEVEL_STRICT}.
     * @return This configuration, never {@code null}.
     */
    ProjectBuildingRequest setValidationLevel( int validationLevel );

    /**
     * Gets the level of validation to perform on processed models.
     *
     * @return The level of validation to perform on processed models.
     */
    int getValidationLevel();

    // Profiles

    /**
     * Set any active profiles that the {@link ProjectBuilder} should consider while constructing
     * a {@link MavenProject}.
     */
    void setActiveProfileIds( List<String> activeProfileIds );

    List<String> getActiveProfileIds();

    void setInactiveProfileIds( List<String> inactiveProfileIds );

    List<String> getInactiveProfileIds();

    /**
     * Add a {@link org.apache.maven.model.Profile} that has come from an external source. This may be from a custom
     * configuration like the MavenCLI settings.xml file, or from a custom dialog in an IDE integration like M2Eclipse.
     * @param profile
     */
    void addProfile( Profile profile );

    void setProfiles( List<Profile> profiles );

    List<Profile> getProfiles();

    /**
     * Gets the start time of the build.
     *
     * @return The start time of the build or {@code null} if unknown.
     */
    Date getBuildStartTime();

    /**
     * Sets the start time of the build.
     *
     * @param buildStartTime The start time of the build, may be {@code null}.
     * @return This request, never {@code null}.
     */
    void setBuildStartTime( Date buildStartTime );

    ArtifactTransferListener getTransferListener();

    void setTransferListener( ArtifactTransferListener transferListener );

}
