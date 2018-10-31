package io.snyk.maven.plugins;

import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by dror on 16/01/2017.
 *
 * Responsible for traversing a project's dependency using Aether's capabilities
 */
public class ProjectTraversal {

    private MavenProject project;
    private RepositorySystem repoSystem;
    private RepositorySystemSession repoSession;

    private JSONObject tree;
    public JSONObject getTree() { return this.tree; }

    public ProjectTraversal(MavenProject project,
                            RepositorySystem repoSystem,
                            RepositorySystemSession repoSession) {
        if(project == null || repoSystem == null || repoSession == null) {
            throw new InvalidParameterException();
        }

        this.project = project;
        this.repoSystem = repoSystem;
        this.repoSession = repoSession;

        try {
            this.collectDependencies();
        } catch (DependencyCollectionException e) {
            this.tree = new JSONObject();
        }
    }

    private void collectDependencies() throws DependencyCollectionException {
        Artifact artifact = new DefaultArtifact(
            project.getGroupId() + ":" +
            project.getArtifactId() + ":" +
            project.getVersion()
        );

        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRoot( new Dependency( artifact, JavaScopes.COMPILE ) );
        collectRequest.setRepositories( newRepositories() );

        CollectResult collectResult = repoSystem.collectDependencies( repoSession, collectRequest );
        DependencyNode node = collectResult.getRoot();

        this.tree = getJsonTree(node);
    }

    private JSONObject getJsonTree(DependencyNode node) {
        JSONObject root = getJsonNode(node, null);

        root.put("packageFormatVersion", "mvn:0.0.1");

        return root;
    }

    private JSONObject getJsonNode(DependencyNode depNode, JSONArray ancestors) {
        Artifact artifact = depNode.getArtifact();
        JSONObject treeNode = createTreeNode(artifact, ancestors);

        List<DependencyNode> children = depNode.getChildren();
        JSONObject dependencies = new JSONObject();
        for(DependencyNode childDep : children) {
            Artifact childArtifact = childDep.getArtifact();
            JSONObject childNode = getJsonNode(childDep, (JSONArray)treeNode.get("from"));
            dependencies.put(childArtifact.getGroupId() + ":" + childArtifact.getArtifactId(), childNode);
        }
        treeNode.put("dependencies", dependencies);

        return treeNode;
    }

    private JSONObject createTreeNode(Artifact artifact, JSONArray ancestors) {
        JSONObject treeNode = new JSONObject();

        treeNode.put("groupId", artifact.getGroupId());
        treeNode.put("artifactId", artifact.getArtifactId());
        treeNode.put("packaging", artifact.getExtension());
        treeNode.put("version", artifact.getVersion());
        treeNode.put("name", artifact.getGroupId() + ":" + artifact.getArtifactId());

        JSONArray from = new JSONArray();
        if(ancestors != null) {
            from.addAll(ancestors);
        }
        from.add(artifact.getGroupId() + ":" + artifact.getArtifactId() + "@" + artifact.getVersion());
        treeNode.put("from", from);

        return treeNode;
    }

    private static List<RemoteRepository> newRepositories()
    {
        return new ArrayList<>( Arrays.asList( newCentralRepository() ) );
    }

    private static RemoteRepository newCentralRepository()
    {
        return new RemoteRepository.Builder(
            "central",
            "default",
            "http://central.maven.org/maven2/"
        ).build();
    }

}
