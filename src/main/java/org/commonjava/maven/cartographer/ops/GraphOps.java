package org.commonjava.maven.cartographer.ops;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.commonjava.maven.atlas.graph.filter.ProjectRelationshipFilter;
import org.commonjava.maven.atlas.graph.model.EProjectGraph;
import org.commonjava.maven.atlas.graph.model.EProjectWeb;
import org.commonjava.maven.atlas.graph.rel.ProjectRelationship;
import org.commonjava.maven.atlas.graph.spi.GraphDriverException;
import org.commonjava.maven.atlas.graph.traverse.BuildOrderTraversal;
import org.commonjava.maven.atlas.graph.traverse.model.BuildOrder;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.cartographer.data.CartoDataException;
import org.commonjava.maven.cartographer.data.CartoDataManager;
import org.commonjava.maven.cartographer.util.ProjectVersionRefComparator;
import org.commonjava.util.logging.Logger;

@ApplicationScoped
public class GraphOps
{

    private final Logger logger = new Logger( getClass() );

    @Inject
    private CartoDataManager data;

    protected GraphOps()
    {
    }

    public GraphOps( final CartoDataManager data )
    {
        this.data = data;
    }

    public void reindexAll()
        throws CartoDataException
    {
        data.reindexAll();
    }

    public void reindex( final ProjectVersionRef ref )
        throws CartoDataException
    {
        if ( ref != null )
        {
            data.reindex( ref );
        }
    }

    public Map<ProjectVersionRef, Set<String>> getAllErrors()
        throws CartoDataException
    {
        logger.info( "Retrieving ALL project errors" );
        return data.getAllProjectErrors();
    }

    public Map<ProjectVersionRef, Set<String>> getErrors( final ProjectVersionRef ref )
        throws CartoDataException
    {
        Map<ProjectVersionRef, Set<String>> errors = null;
        if ( ref != null )
        {
            logger.info( "Retrieving project errors in graph: %s", ref );
            errors = data.getProjectErrorsInGraph( ref );
        }

        return errors;
    }

    public Set<ProjectVersionRef> getAllIncomplete( final ProjectRelationshipFilter filter )
        throws CartoDataException
    {
        final Set<ProjectVersionRef> incomplete = data.getAllIncompleteSubgraphs( filter );

        return incomplete;
    }

    public Set<ProjectVersionRef> getIncomplete( final ProjectVersionRef ref, final ProjectRelationshipFilter filter )
        throws CartoDataException
    {
        Set<ProjectVersionRef> incomplete = null;
        if ( ref != null )
        {
            incomplete = data.getIncompleteSubgraphsFor( filter, ref );
        }

        return incomplete;
    }

    public Set<ProjectVersionRef> getAllVariable( final ProjectRelationshipFilter filter )
        throws CartoDataException
    {
        return data.getAllVariableSubgraphs( filter );
    }

    public Set<ProjectVersionRef> getVariable( final ProjectVersionRef ref, final ProjectRelationshipFilter filter )
        throws CartoDataException
    {
        if ( ref != null )
        {
            return data.getVariableSubgraphsFor( filter, ref );
        }

        return null;
    }

    public List<ProjectVersionRef> getAncestry( final ProjectVersionRef ref )
        throws CartoDataException
    {
        return data.getAncestry( ref );
    }

    public BuildOrder getBuildOrder( final ProjectVersionRef ref, final ProjectRelationshipFilter filter )
        throws CartoDataException
    {
        final EProjectGraph graph = data.getProjectGraph( ref );

        if ( graph != null )
        {
            final BuildOrderTraversal traversal = new BuildOrderTraversal( filter );

            logger.info( "Performing build-order traversal for graph: %s", ref );
            try
            {
                graph.traverse( traversal );
            }
            catch ( final GraphDriverException e )
            {
                throw new CartoDataException( "Failed to construct build order for: %s. Reason: %s", e, ref,
                                              e.getMessage() );
            }

            return traversal.getBuildOrder();
        }

        return null;
    }

    public EProjectGraph getProjectGraph( final ProjectRelationshipFilter filter, final ProjectVersionRef ref )
        throws CartoDataException
    {
        return data.getProjectGraph( filter, ref );
    }

    public EProjectWeb getProjectWeb( final ProjectRelationshipFilter filter, final ProjectVersionRef... refs )
        throws CartoDataException
    {
        return data.getProjectWeb( filter, refs );
    }

    public Set<String> getProjectErrors( final ProjectVersionRef ref )
        throws CartoDataException
    {
        return data.getErrors( ref );
    }

    public List<ProjectVersionRef> listProjects( final String groupIdPattern, final String artifactIdPattern )
        throws CartoDataException
    {
        final Set<ProjectVersionRef> all = data.getAllStoredProjectRefs();
        final List<ProjectVersionRef> matching = new ArrayList<ProjectVersionRef>();
        if ( all != null )
        {
            if ( groupIdPattern != null || artifactIdPattern != null )
            {
                final String gip = groupIdPattern == null ? ".*" : groupIdPattern.replaceAll( "\\*", ".*" );
                final String aip = artifactIdPattern == null ? ".*" : artifactIdPattern.replaceAll( "\\*", ".*" );

                logger.info( "Filtering %d projects using groupId pattern: '%s' and artifactId pattern: '%s'",
                             all.size(), gip, aip );

                for ( final ProjectVersionRef ref : all )
                {
                    if ( ref.getGroupId()
                            .matches( gip ) && ref.getArtifactId()
                                                  .matches( aip ) )
                    {
                        matching.add( ref );
                    }
                }
            }
            else
            {
                logger.info( "Returning all %d projects", all.size() );
                matching.addAll( all );
            }

        }

        if ( !matching.isEmpty() )
        {
            Collections.sort( matching, new ProjectVersionRefComparator() );

        }

        return matching;
    }

    public ProjectVersionRef getProjectParent( final ProjectVersionRef projectVersionRef )
        throws CartoDataException
    {
        return data.getParent( projectVersionRef );
    }

    public Set<ProjectRelationship<?>> getDirectRelationshipsFrom( final ProjectVersionRef ref,
                                                                   final ProjectRelationshipFilter filter )
        throws CartoDataException
    {
        return data.getAllDirectRelationshipsWithExactSource( ref, filter );
    }

    public Set<ProjectRelationship<?>> getDirectRelationshipsTo( final ProjectVersionRef ref,
                                                                 final ProjectRelationshipFilter filter )
        throws CartoDataException
    {
        return data.getAllDirectRelationshipsWithExactTarget( ref, filter );
    }

}