package ch.dfa.dfa_tool.models;


import ch.dfa.dfa_tool.services.DateExtractor;
import ch.dfa.dfa_tool.services.GitHubMinerService;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

import org.eclipse.jgit.revwalk.RevCommit;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static java.lang.Math.toIntExact;

@Entity
@Getter
@Setter
public class Renamedpath {

    private int index;
    private String renamedPath;
    private String originPath;
    private ArrayList<RevCommit> RevHistory;


    public Renamedpath(int index, String renamedPath, String originPath, ArrayList<RevCommit> RevHistory){
        this.index = index;
        this.renamedPath = renamedPath;
        this.originPath = originPath;
        this.RevHistory = RevHistory;
    }

}
