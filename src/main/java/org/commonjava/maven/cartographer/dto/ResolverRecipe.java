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
package org.commonjava.maven.cartographer.dto;

import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import org.commonjava.maven.cartographer.discover.DefaultDiscoveryConfig;
import org.commonjava.maven.cartographer.discover.DiscoveryConfig;
import org.commonjava.maven.cartographer.preset.PresetSelector;
import org.commonjava.maven.galley.model.Location;

public class ResolverRecipe
{

    protected GraphComposition graphComposition;

    protected String workspaceId;

    protected Set<String> patcherIds;

    protected Location sourceLocation;

    protected Integer timeoutSecs;

    protected boolean resolve;

    public String getWorkspaceId()
    {
        return workspaceId;
    }

    public Location getSourceLocation()
    {
        return sourceLocation;
    }

    public void setWorkspaceId( final String workspaceId )
    {
        this.workspaceId = workspaceId;
    }

    public void setSourceLocation( final Location source )
    {
        this.sourceLocation = source;
    }

    public DiscoveryConfig getDiscoveryConfig()
        throws URISyntaxException
    {
        final DefaultDiscoveryConfig ddc = new DefaultDiscoveryConfig( getSourceLocation().toString() );
        ddc.setEnabled( true );

        return ddc;
    }

    public Integer getTimeoutSecs()
    {
        return timeoutSecs == null ? 10 : timeoutSecs;
    }

    public void setTimeoutSecs( final Integer timeoutSecs )
    {
        this.timeoutSecs = timeoutSecs;
    }

    public Set<String> getPatcherIds()
    {
        return patcherIds;
    }

    public void setPatcherIds( final Set<String> patcherIds )
    {
        this.patcherIds = patcherIds;
    }

    public GraphComposition getGraphComposition()
    {
        return graphComposition;
    }

    public void setGraphComposition( final GraphComposition graphComposition )
    {
        this.graphComposition = graphComposition;
    }

    public void resolveFilters( final PresetSelector presets, final String defaultPreset )
    {
        graphComposition.resolveFilters( presets, defaultPreset );
    }

    public ResolverRecipe()
    {
        super();
    }

    public boolean isResolve()
    {
        return resolve;
    }

    public void setResolve( final boolean resolve )
    {
        this.resolve = resolve;
    }

    public boolean isValid()
    {
        return getWorkspaceId() != null && getSourceLocation() != null && graphComposition != null && graphComposition.isValid();
    }

    public void normalize()
    {
        graphComposition.normalize();
        normalize( patcherIds );
    }

    protected void normalize( final Collection<?> coll )
    {
        if ( coll == null )
        {
            return;
        }

        for ( final Iterator<?> it = coll.iterator(); it.hasNext(); )
        {
            if ( it.next() == null )
            {
                it.remove();
            }
        }
    }
}