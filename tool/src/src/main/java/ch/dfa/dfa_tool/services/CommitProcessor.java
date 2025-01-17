package ch.dfa.dfa_tool.services;

import ch.dfa.dfa_tool.models.ChangedFile;
import com.gitblit.models.PathModel;
import com.gitblit.utils.JGitUtils;
import com.google.common.collect.Lists;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by salizumberi-laptop on 30.10.2016.
 */
public class CommitProcessor {
    private static ArrayList<Date> getCommitDates(List<RevCommit> historyOfFile) {
        ArrayList<Date> commitDates = new ArrayList<>();
        for (int i = 0; i < historyOfFile.size(); i++) {
            commitDates.add(new Date(historyOfFile.get(i).getCommitTime() * 1000L));
        }
        return commitDates;
    }

    public static <E> List<E> toList(Iterable<E> iterable) {
        if (iterable instanceof List) {
            return (List<E>) iterable;
        }
        ArrayList<E> list = new ArrayList<E>();
        if (iterable != null) {
            for (E e : iterable) {
                list.add(e);
            }
        }
        return list;
    }
    public ChangedFile getDockerfileOfSnapshot(RevCommit revCommit,Repository repository, Git git, String dockerpath) throws MissingObjectException, IncorrectObjectTypeException, IOException {
        RevWalk walk = new RevWalk(repository);
        walk.reset();
        git.close();
        RevCommit dockerCommit = walk.parseCommit(revCommit.getId());
        List<PathModel.PathChangeModel> files = JGitUtils.getFilesInCommit(repository, dockerCommit);
        for (PathModel.PathChangeModel file : files){
            
            if (file.name.equals(dockerpath)) {
                
                System.out.println(file.name + " == " + dockerpath);
                ChangedFile changedFile = new ChangedFile(
                    file.path,
                    "",
                    "",
                    "",
                    "",
                    file.mode, file.changeType, file.deletions, file.insertions,
                    0, 0, file.commitId, dockerpath);

                    return changedFile;
                    
            }
        }
        return null;

    }
    public List<ChangedFile> getChangedFilesWithinCommit(RevCommit revCommit, Repository repository, int rangeSize,
            int range_index, Git git, String repoPath) throws IOException, GitAPIException {
        // System.out.println("6.3.1 get Changed Files of a Commit
        // (getChangedFilesWithinCommit) with the range: " + +range_index + " &
        // rangeSize: " + rangeSize);
        RevWalk walk = new RevWalk(repository);
        walk.reset();
        git.close();
        RevCommit dockerCommit = walk.parseCommit(revCommit.getId());
        
        // Iterable<RevCommit> commits = git.log().all().call();
        // List<RevCommit> xcommits = toList(commits);
        git.close();
        walk.reset();

        List<Ref> branches = git.branchList().call();
        
        List<RevCommit> commitsList = null;
        for (Ref branch : branches) {
            String branchName = branch.getName();
            Iterable<RevCommit> branchCommits = git.log().add(repository.resolve(branchName)).call();
            commitsList = Lists.newArrayList(branchCommits.iterator());

        }

        List<RevCommit> dockys = new ArrayList<>();
        RevCommit foundCommit = null;
        List<PathModel.PathChangeModel> files = new ArrayList<>();
        boolean dockyFound = false;
        finder: if (range_index < 0) {
            int index = 0;
            for (RevCommit commit : commitsList) {
                if (commit.getId().equals(dockerCommit.getId())) {
                    dockys.add(commit);
                    while (!dockyFound) {
                        if (index == range_index) {
                            for (RevCommit docky : dockys) {
                                List<PathModel.PathChangeModel> foundFiles = null;
                                try {
                                    System.out.println("Ich bin unter commit für ein changedFile :" + docky );
                                    for (RevCommit docky_T : dockys) {
                                        System.out.println("Das sind Commits" +  docky_T);
                                    }
                                    foundFiles = JGitUtils.getFilesInCommit(repository, docky);
                                } catch (Exception e) {
                                    System.out.println(" Ich bin der 1 Missing Blob: " + docky );
                                    System.exit(1);
                                }
                                for (PathModel.PathChangeModel found : foundFiles) {
                                    
                                    files.add(found);
                                    
                                }
                            }
                            dockyFound = true;
                            break finder;
                        } else {
                            List<RevCommit> tempDockys = new ArrayList<>();
                            for (RevCommit docky : dockys) {
                                for (int i = 0; i < docky.getParentCount(); i++) {
                                    RevCommit parent = docky.getParents()[i];
                                    tempDockys.add(parent);
                                    walk.reset();
                                }
                            }
                            dockys = tempDockys;
                        }
                        index--;
                    }
                    // Start with: git://github.com/deepaklukose/grpc.git + deepaklukose/grpc +
                    // tools/dockerfile/distribtest/csharp_wheezy_x64/Dockerfile
                }
            }
        } else if (range_index > 0) {

            System.out.println("Ich bin über 0" + dockerCommit + " in Repo: " + repository);
            int index = 0;
            dockys.add(dockerCommit);
            while (!dockyFound) {
                if (index == range_index) {
                    try
                        {
                        for (RevCommit docky : dockys) {
                        //System.out.println("Commit: " + docky);
                        List<PathModel.PathChangeModel> foundFiles = JGitUtils.getFilesInCommit(repository, docky);
                        
                        
                            for (PathModel.PathChangeModel found : foundFiles) 
                            {
                                files.add(found);
                            }
                        }

                    dockyFound = true;

                    }catch(Exception e)

                    {
                    for (RevCommit docky_T : dockys) {
                        System.out.println("Das sind Commits" +  docky_T);
                    }
                    
                    System.out.println(e.getStackTrace());
                    System.exit(1);
                    }
                } else {

                    List<RevCommit> tempDockys = new ArrayList<>();
                    parent: for (RevCommit children : commitsList) {
                        for (int i = 0; i < children.getParentCount(); i++) {
                            RevCommit parent = children.getParents()[i];
                            for (RevCommit docky : dockys) {
                                if (parent.getName().equals(docky.getName())) {
                                    tempDockys.add(walk.parseCommit(children.getId()));
                                    walk.reset();
                                    index++;
                                    break parent;
                                }
                            }
                        }
                    }
                    if (tempDockys.size() == 0) {
                        dockyFound = true;
                    }
                    dockys = tempDockys;
                }
            }
        } else {
            dockys.add(dockerCommit);
            try{
                
                System.out.println("Ich bin 0" + dockerCommit + " in Repo: " + repository);
                files = JGitUtils.getFilesInCommit(repository, dockerCommit);

            }catch(Exception mo)
            {
                System.out.println("Missing Blob from " + dockerCommit);
            }

            foundCommit = dockys.get(0);
        }

        List<ChangedFile> changedFiles = new ArrayList<>();

        for (PathModel.PathChangeModel model : files) {
            String fullFileName = null;
            String fileName = null;
            String fileType = null;
            String filePath = null;
            String[] pathTokens = model.name.split("/");
  
            if (pathTokens.length == 1) {
                fullFileName = model.name;
                filePath = "";
            } else {
                filePath = "";
                for (int i = 0; i < pathTokens.length - 1; i++) {
                    filePath += pathTokens[i] + "/";
                }
                try {
                    fullFileName = model.name.replaceAll(filePath, "");
                } catch (Exception e) {
                    e.printStackTrace();
                    fullFileName = "";
                }
            }
            if (fullFileName.contains(".")) {
                String[] parts = fullFileName.split("\\.");
                fileName = parts[0];
                fileType = parts[1];
            } else {
                fileName = fullFileName;
                fileType = "";
            }
            ChangedFile changedFile = new ChangedFile(
                    model.path,
                    fullFileName,
                    fileName,
                    filePath,
                    fileType,
                    model.mode, model.changeType, model.deletions, model.insertions,
                    range_index, rangeSize, model.commitId, repoPath);
            
            System.out.println("Das ist das File:" + fullFileName + model.changeType + model.deletions + model.insertions);
            changedFiles.add(changedFile);
        }
        return changedFiles;
    }

}
