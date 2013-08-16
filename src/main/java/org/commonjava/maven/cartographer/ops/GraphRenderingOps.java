package org.commonjava.maven.cartographer.ops;

import static org.commonjava.maven.cartographer.agg.AggregationUtils.collectProjectReferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.commonjava.maven.atlas.graph.filter.ProjectRelationshipFilter;
import org.commonjava.maven.atlas.graph.model.EProjectGraph;
import org.commonjava.maven.atlas.graph.model.EProjectWeb;
import org.commonjava.maven.atlas.graph.rel.DependencyRelationship;
import org.commonjava.maven.atlas.graph.rel.ProjectRelationship;
import org.commonjava.maven.atlas.graph.spi.GraphDriverException;
import org.commonjava.maven.atlas.graph.traverse.FilteringTraversal;
import org.commonjava.maven.atlas.graph.traverse.ProjectNetTraversal;
import org.commonjava.maven.atlas.graph.traverse.TransitiveDependencyTraversal;
import org.commonjava.maven.atlas.graph.traverse.print.DependencyTreeRelationshipPrinter;
import org.commonjava.maven.atlas.graph.traverse.print.StructurePrintingTraversal;
import org.commonjava.maven.atlas.ident.DependencyScope;
import org.commonjava.maven.atlas.ident.ref.ProjectRef;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.atlas.ident.ref.VersionlessArtifactRef;
import org.commonjava.maven.atlas.ident.version.CompoundVersionSpec;
import org.commonjava.maven.atlas.ident.version.SingleVersion;
import org.commonjava.maven.atlas.ident.version.VersionSpec;
import org.commonjava.maven.cartographer.agg.ProjectRefCollection;
import org.commonjava.maven.cartographer.data.CartoDataException;
import org.commonjava.maven.cartographer.data.CartoDataManager;

@ApplicationScoped
public class GraphRenderingOps
{

    @Inject
    private CartoDataManager data;

    protected GraphRenderingOps()
    {
    }

    public GraphRenderingOps( final CartoDataManager data )
    {
        this.data = data;
    }

    public String depTree( final ProjectVersionRef ref, final ProjectRelationshipFilter filter,
                           final DependencyScope scope, final boolean collapseTransitives )
        throws CartoDataException
    {
        final EProjectGraph graph = data.getProjectGraph( ref );
        if ( graph != null )
        {
            ProjectNetTraversal t;
            if ( collapseTransitives )
            {
                t = new TransitiveDependencyTraversal( filter );
            }
            else
            {
                t = new FilteringTraversal( filter );
            }

            final StructurePrintingTraversal printer =
                new StructurePrintingTraversal( t, new DependencyTreeRelationshipPrinter() );

            try
            {
                graph.traverse( printer );
            }
            catch ( final GraphDriverException e )
            {
                throw new CartoDataException( "Failed to traverse for dependency-tree generation of: %s. Reason: %s",
                                              ref, e.getMessage() );
            }

            return printer.printStructure( ref );
        }

        return null;
    }

    public Model generateBOM( final ProjectVersionRef bomCoord, final ProjectRelationshipFilter filter,
                              final ProjectVersionRef... roots )
        throws CartoDataException
    {
        final EProjectWeb web = data.getProjectWeb( filter, roots );

        if ( web == null )
        {
            return null;
        }

        final Map<ProjectRef, ProjectRefCollection> projects = collectProjectReferences( web );

        final Model model = new Model();
        model.setGroupId( bomCoord.getGroupId() );
        model.setArtifactId( bomCoord.getArtifactId() );
        model.setVersion( ( (SingleVersion) bomCoord.getVersionSpec() ).renderStandard() );
        model.setPackaging( "pom" );
        model.setName( bomCoord.getArtifactId() + ":: Bill of Materials" );
        model.setDescription( "Generated by Cartographer at " + new Date() );

        final DependencyManagement dm = new DependencyManagement();
        model.setDependencyManagement( dm );

        for ( final Map.Entry<ProjectRef, ProjectRefCollection> entry : projects.entrySet() )
        {
            final ProjectRef r = entry.getKey();
            final ProjectRefCollection prc = entry.getValue();

            final VersionSpec spec = generateVersionSpec( prc.getVersionRefs() );
            final Set<VersionlessArtifactRef> arts = prc.getVersionlessArtifactRefs();
            if ( arts == null )
            {
                continue;
            }

            for ( final VersionlessArtifactRef artifact : arts )
            {
                final Dependency d = new Dependency();

                d.setGroupId( r.getGroupId() );
                d.setArtifactId( r.getArtifactId() );
                d.setVersion( spec.renderStandard() );
                if ( !"jar".equals( artifact.getType() ) )
                {
                    d.setType( artifact.getType() );
                }

                if ( artifact.getClassifier() != null )
                {
                    d.setClassifier( artifact.getClassifier() );
                }

                dm.addDependency( d );
            }
        }

        return model;
    }

    private VersionSpec generateVersionSpec( final Set<ProjectVersionRef> refs )
    {
        final List<VersionSpec> versions = new ArrayList<VersionSpec>();
        for ( final ProjectVersionRef ref : refs )
        {
            final VersionSpec spec = ref.getVersionSpec();
            versions.add( spec );
        }

        Collections.sort( versions );

        if ( versions.size() == 1 )
        {
            return versions.get( 0 );
        }

        return new CompoundVersionSpec( null, versions );
    }

    public String dotfile( final ProjectVersionRef coord, final ProjectRelationshipFilter filter,
                           final ProjectVersionRef... roots )
        throws CartoDataException
    {
        final EProjectWeb web = data.getProjectWeb( filter, roots );

        if ( web != null )
        {
            final Set<ProjectVersionRef> refs = new HashSet<ProjectVersionRef>( web.getAllProjects() );
            final Set<ProjectRelationship<?>> rels = web.getAllRelationships();

            final Map<ProjectVersionRef, String> aliases = new HashMap<ProjectVersionRef, String>();

            final StringBuilder sb = new StringBuilder();
            sb.append( "digraph " )
              .append( cleanDotName( coord.getGroupId() ) )
              .append( '_' )
              .append( cleanDotName( coord.getArtifactId() ) )
              .append( '_' )
              .append( cleanDotName( ( (SingleVersion) coord.getVersionSpec() ).renderStandard() ) )
              .append( " {" );

            sb.append( "\nsize=\"300,20\"; resolution=72;\n" );

            for ( final ProjectVersionRef r : refs )
            {
                final String aliasBase = cleanDotName( r.toString() );

                String alias = aliasBase;
                int idx = 2;
                while ( aliases.containsValue( alias ) )
                {
                    alias = aliasBase + idx++;
                }

                aliases.put( r, alias );

                sb.append( "\n" )
                  .append( alias )
                  .append( " [label=\"" )
                  .append( r )
                  .append( "\"];" );
            }

            sb.append( "\n" );

            for ( final ProjectRelationship<?> rel : rels )
            {
                final String da = aliases.get( rel.getDeclaring() );
                final String ta = aliases.get( rel.getTarget()
                                                  .asProjectVersionRef() );

                sb.append( "\n" )
                  .append( da )
                  .append( " -> " )
                  .append( ta );

                appendRelationshipInfo( rel, sb );
                sb.append( ";" );
            }

            sb.append( "\n\n}\n" );
            return sb.toString();
        }

        return null;
    }

    private String cleanDotName( final String src )
    {
        return src.replace( ':', '_' )
                  .replace( '.', '_' )
                  .replace( '-', '_' );
    }

    @SuppressWarnings( "incomplete-switch" )
    private void appendRelationshipInfo( final ProjectRelationship<?> rel, final StringBuilder sb )
    {
        sb.append( " [type=\"" )
          .append( rel.getType()
                      .name() )
          .append( "\"" );
        switch ( rel.getType() )
        {
            case DEPENDENCY:
            {
                sb.append( " managed=\"" )
                  .append( ( (DependencyRelationship) rel ).isManaged() )
                  .append( "\"" );
                sb.append( " scope=\"" )
                  .append( ( (DependencyRelationship) rel ).getScope()
                                                           .realName() )
                  .append( "\"" );
                break;
            }
            case PLUGIN:
            {
                sb.append( " managed=\"" )
                  .append( ( (DependencyRelationship) rel ).isManaged() )
                  .append( "\"" );
                break;
            }
            case PLUGIN_DEP:
            {
                sb.append( " managed=\"" )
                  .append( ( (DependencyRelationship) rel ).isManaged() )
                  .append( "\"" );
                break;
            }
        }
        sb.append( "]" );
    }
}