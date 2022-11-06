package ch.dfa.dfa_tool.services;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.File;
import java.io.IOException;

public class GitCloner {

    public GitCloner() throws Exception {
    }

    public void cloneRepository(String gitUrl, String localPath) throws Exception {
        File repositoryFile = new File(localPath);
        if (repositoryFile.exists() && repositoryFile.isDirectory()) {

        } else {
            System.out.println("Clone git:");
            Git git = Git.cloneRepository().setURI(gitUrl).setCredentialsProvider(new UsernamePasswordCredentialsProvider("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", "")).setDirectory(new File(localPath)).call();
            git.close();
        }
    }

    public Repository getRepository(String localPath) throws IOException {
        File repositoryFile = new File(localPath);
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        return builder.setGitDir(repositoryFile).readEnvironment().findGitDir().build();
    }
}
