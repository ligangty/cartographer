/*******************************************************************************
 * Copyright (C) 2014 John Casey.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package org.commonjava.maven.cartographer.discover.post.patch;

import static org.apache.commons.lang.StringUtils.join;
import static org.commonjava.maven.cartographer.discover.DiscoveryContextConstants.POM_VIEW_CTX_KEY;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.commonjava.maven.atlas.graph.rel.DependencyRelationship;
import org.commonjava.maven.atlas.graph.rel.ProjectRelationship;
import org.commonjava.maven.atlas.graph.util.RelationshipUtils;
import org.commonjava.maven.atlas.ident.DependencyScope;
import org.commonjava.maven.atlas.ident.ref.InvalidRefException;
import org.commonjava.maven.atlas.ident.ref.ProjectRef;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.atlas.ident.ref.VersionlessArtifactRef;
import org.commonjava.maven.atlas.ident.version.InvalidVersionSpecificationException;
import org.commonjava.maven.cartographer.discover.DiscoveryResult;
import org.commonjava.maven.galley.maven.GalleyMavenException;
import org.commonjava.maven.galley.maven.model.view.DependencyView;
import org.commonjava.maven.galley.maven.model.view.MavenPomView;
import org.commonjava.maven.galley.model.Location;
import org.commonjava.util.logging.Logger;

public class DependencyPluginPatcher
    implements DepgraphPatcher
{

    private static final String[] PATHS =
        {
            "/project/build/plugins/plugin[artifactId/text()=\"maven-dependency-plugin\"]/executions/execution/configuration/artifactItems/artifactItem",
            "/project/build/plugins/plugin[artifactId/text()=\"maven-dependency-plugin\"]/configuration/artifactItems/artifactItem",
            "/project/build/pluginManagement/plugins/plugin[artifactId/text()=\"maven-dependency-plugin\"]/executions/execution/configuration/artifactItems/artifactItem",
            "/project/build/pluginManagement/plugins/plugin[artifactId/text()=\"maven-dependency-plugin\"]/configuration/artifactItems/artifactItem",
            "/project/profiles/profile/build/plugins/plugin[artifactId/text()=\"maven-dependency-plugin\"]/executions/execution/configuration/artifactItems/artifactItem",
            "/project/profiles/profile/build/plugins/plugin[artifactId/text()=\"maven-dependency-plugin\"]/configuration/artifactItems/artifactItem",
            "/project/profiles/profile/build/pluginManagement/plugins/plugin[artifactId/text()=\"maven-dependency-plugin\"]/executions/execution/configuration/artifactItems/artifactItem",
            "/project/profiles/profile/build/pluginManagement/plugins/plugin[artifactId/text()=\"maven-dependency-plugin\"]/configuration/artifactItems/artifactItem" };

    private final Logger logger = new Logger( getClass() );

    @Override
    public void patch( final DiscoveryResult result, final List<? extends Location> locations, final Map<String, Object> context )
    {
        final ProjectVersionRef ref = result.getSelectedRef();
        try
        {
            final MavenPomView pomView = (MavenPomView) context.get( POM_VIEW_CTX_KEY );

            // get all artifactItems with a version element. Only these need to be verified.
            final String depArtifactItemsPath = join( PATHS, "|" );

            logger.info( "Looking for dependency-plugin usages matching: '%s'", depArtifactItemsPath );
            // TODO: Switch to a DependencyView here, with path to dependencyManagement...
            final List<DependencyView> depArtifactItems = pomView.getAllDependenciesMatching( depArtifactItemsPath );
            if ( depArtifactItems == null || depArtifactItems.isEmpty() )
            {
                return;
            }

            final Set<ProjectRelationship<?>> accepted = new HashSet<ProjectRelationship<?>>( result.getAcceptedRelationships() );

            final Map<VersionlessArtifactRef, DependencyRelationship> concreteDeps = new HashMap<VersionlessArtifactRef, DependencyRelationship>();
            for ( final ProjectRelationship<?> rel : accepted )
            {
                if ( rel instanceof DependencyRelationship && !rel.isManaged() )
                {
                    final VersionlessArtifactRef key = new VersionlessArtifactRef( rel.getTargetArtifact() );
                    logger.info( "Mapping existing dependency via key: %s", key );
                    concreteDeps.put( key, (DependencyRelationship) rel );
                }
            }

            calculateDependencyPluginPatch( depArtifactItems, concreteDeps, ref, pomView, result );
        }
        catch ( final GalleyMavenException e )
        {
            logger.error( "Failed to build/query MavenPomView for: %s from: %s. Reason: %s", e, ref, locations, e.getMessage() );
        }
        catch ( final InvalidVersionSpecificationException e )
        {
            logger.error( "Failed to build/query MavenPomView for: %s from: %s. Reason: %s", e, ref, locations, e.getMessage() );
        }
        catch ( final InvalidRefException e )
        {
            logger.error( "Failed to build/query MavenPomView for: %s from: %s. Reason: %s", e, ref, locations, e.getMessage() );
        }
    }

    private void calculateDependencyPluginPatch( final List<DependencyView> depArtifactItems,
                                                 final Map<VersionlessArtifactRef, DependencyRelationship> concreteDeps, final ProjectVersionRef ref,
                                                 final MavenPomView pomView, final DiscoveryResult result )
    {
        logger.info( "Detected %d dependency-plugin artifactItems that need to be accounted for in dependencies...", depArtifactItems == null ? 0
                        : depArtifactItems.size() );
        if ( depArtifactItems != null && !depArtifactItems.isEmpty() )
        {
            final URI source = result.getSource();
            for ( final DependencyView depView : depArtifactItems )
            {
                try
                {
                    final URI pomLocation = RelationshipUtils.profileLocation( depView.getProfileId() );
                    final VersionlessArtifactRef depRef = depView.asVersionlessArtifactRef();
                    logger.info( "Detected dependency-plugin usage with key: %s", depRef );

                    final DependencyRelationship dep = concreteDeps.get( depRef );
                    if ( dep != null )
                    {
                        if ( !DependencyScope.runtime.implies( dep.getScope() )
                            && ( dep.getPomLocation()
                                    .equals( pomLocation ) || dep.getPomLocation() == RelationshipUtils.POM_ROOT_URI ) )
                        {
                            logger.info( "Correcting scope for: %s", dep );

                            if ( !result.removeDiscoveredRelationship( dep ) )
                            {
                                logger.error( "Failed to remove: %s", dep );
                            }

                            final Set<ProjectRef> excludes = dep.getExcludes();
                            final ProjectRef[] excludedRefs =
                                excludes == null ? new ProjectRef[0] : excludes.toArray( new ProjectRef[excludes.size()] );

                            final DependencyRelationship replacement =
                                new DependencyRelationship( dep.getSources(), ref, dep.getTargetArtifact(), DependencyScope.embedded, dep.getIndex(),
                                                            false, excludedRefs );

                            if ( !result.addDiscoveredRelationship( replacement ) )
                            {
                                logger.error( "Failed to inject: %s", replacement );
                            }
                        }
                    }
                    else if ( depView.getVersion() != null )
                    {
                        logger.info( "Injecting new dep: %s", depView.asArtifactRef() );
                        final DependencyRelationship injected =
                            new DependencyRelationship( source, RelationshipUtils.profileLocation( depView.getProfileId() ), ref,
                                                        depView.asArtifactRef(), DependencyScope.embedded, concreteDeps.size(), false );

                        if ( !result.addDiscoveredRelationship( injected ) )
                        {
                            logger.error( "Failed to inject: %s", injected );
                        }
                    }
                    else
                    {
                        logger.error( "Invalid dependency referenced in artifactItems of dependency plugin configuration: %s. "
                            + "No version was specified, and it does not reference an actual dependency.", depRef );
                    }
                }
                catch ( final GalleyMavenException e )
                {
                    logger.error( "Dependency is invalid: %s. Reason: %s. Skipping.", e, depView.toXML(), e.getMessage() );
                }
                catch ( final InvalidVersionSpecificationException e )
                {
                    logger.error( "Dependency is invalid: %s. Reason: %s. Skipping.", e, depView.toXML(), e.getMessage() );
                }
                catch ( final InvalidRefException e )
                {
                    logger.error( "Dependency is invalid: %s. Reason: %s. Skipping.", e, depView.toXML(), e.getMessage() );
                }
            }
        }

    }

    @Override
    public String getId()
    {
        return "dependency-plugin";
    }

}