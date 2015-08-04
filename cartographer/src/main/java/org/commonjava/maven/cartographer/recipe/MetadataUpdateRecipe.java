package org.commonjava.maven.cartographer.recipe;

import java.util.Map;

import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;

public class MetadataUpdateRecipe
    extends ProjectGraphRecipe
{

    private Map<String, String> globalMetadata;

    private Map<ProjectVersionRef, Map<String, String>> projectMetadata;

    public Map<String, String> getGlobalMetadata()
    {
        return globalMetadata;
    }

    public void setGlobalMetadata( final Map<String, String> globalMetadata )
    {
        this.globalMetadata = globalMetadata;
    }

    public Map<ProjectVersionRef, Map<String, String>> getProjectMetadata()
    {
        return projectMetadata;
    }

    public void setProjectMetadata( final Map<ProjectVersionRef, Map<String, String>> projectMetadata )
    {
        this.projectMetadata = projectMetadata;
    }

}
