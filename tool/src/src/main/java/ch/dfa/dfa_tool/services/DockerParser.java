package ch.dfa.dfa_tool.services;

import ch.dfa.dfa_tool.models.Snapshot;
import ch.dfa.dfa_tool.models.commands.*;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class DockerParser {
    Snapshot dockerfile = new Snapshot();
    public String localDockerfilePath;
    public String localPath;

    public From from;
    public Maintainer maintainer;
    public Cmd cmd;
    public EntryPoint entryPoint;
    public StopSignal stopSignal;
    public Healthcheck healthcheck;

    public List<Run> runs = new ArrayList<>();
    public List<Label> labels = new ArrayList<>();
    public List<Env> envs = new ArrayList<>();
    public List<Expose> exposes = new ArrayList<>();
    public List<Add> adds = new ArrayList<>();
    public List<Copy> copies = new ArrayList<>();
    public List<Volume> volumes = new ArrayList<>();
    public List<User> users = new ArrayList<>();
    public List<WorkDir> workDirs = new ArrayList<>();
    public List<Arg> args = new ArrayList<>();
    public List<OnBuild> onBuilds = new ArrayList<>();
    public List<Comment> comments = new ArrayList<>();

    public DockerParser(String localpath, String localDockerfilePath) {
        String[] pathTokens = localDockerfilePath.split("/");
        if (pathTokens.length == 1) {
            this.localDockerfilePath = "";
        } else {
            String newPath = "";
            for (int i = 0; i < pathTokens.length - 1; i++) {
                newPath += pathTokens[i] + "/";
            }
            this.localDockerfilePath = newPath;
        }
        this.localPath = localpath;
    }

    public DockerParser(String localpath) {
        this.localDockerfilePath = "";
        this.localPath = localpath;
    }

    public Snapshot getParsedDockerfileObject(File rawDockerfile) throws IOException {
        dockerfile = new Snapshot();
        //System.out.println(rawDockerfile.getPath());
        File fileToBeFlat = new File(rawDockerfile.getPath());
        File flatDockerfile = getFlatDockerFile(fileToBeFlat);
        //printLines(flatDockerfile);
        doClassificationOfLines(flatDockerfile);
        assignToDockerObject(fileToBeFlat);
        return dockerfile;
    }

    public Snapshot getDockerfileObject() throws IOException {
        dockerfile = new Snapshot();
        File fileToBeFlat = new File(this.localPath + "/" + this.localDockerfilePath + "/" + "Dockerfile");
        File flatDockerfile = getFlatDockerFile(fileToBeFlat);
        doClassificationOfLines(flatDockerfile);
        assignToDockerObject(fileToBeFlat);
        return dockerfile;
    }

    public void printLines(File file) throws IOException {
        try (Stream<String> lines = Files.lines(file.toPath(), Charset.defaultCharset())) {
            lines.forEachOrdered(System.out::println);
        }
    }

    public boolean checkForInstruction(String line) {
        String lineU = line.toUpperCase();
        boolean a = isContainExactWord(lineU, "FROM");
        boolean b = isContainExactWord(lineU, "ADD");
        boolean c = isContainExactWord(lineU, "COPY");
        boolean d = isContainExactWord(lineU, "RUN");
        boolean e = isContainExactWord(lineU, "LABEL");
        boolean f = isContainExactWord(lineU, "ENV");
        boolean g = isContainExactWord(lineU, "ARG");
        boolean h = isContainExactWord(lineU, "VOLUME");
        boolean i = isContainExactWord(lineU, "MAINTAINER");
        boolean j = isContainExactWord(lineU, "HEALTHCHECK");
        boolean k = isContainExactWord(lineU, "STOPSIGNAL");
        boolean l = isContainExactWord(lineU, "EXPOSE");
        boolean m = isContainExactWord(lineU, "CMD");
        boolean n = isContainExactWord(lineU, "ENTRYPOINT");
        boolean o = isContainExactWord(lineU, "ONBUILD");
        boolean p = isContainExactWord(lineU, "USER");
        boolean q = isContainExactWord(lineU, "WORKDIR");
        if (a || b || c || d || e || f || g || h || i || j || k || l || m || n || o || p || q) {
            return true;
        } else {
            return false;
        }
    }

    public boolean isContainExactWord(String fullString, String partWord) {
        String pattern = "\\b" + partWord + "\\b";
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(fullString);
        return m.find();
    }

    public List<Comment> getCommentsFromDockerfile(File flatDockerFile, Snapshot snapshot) throws IOException {
        List<Comment> comments = new ArrayList<>();
        FileInputStream fis = new FileInputStream(flatDockerFile);
        BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
        String line = null;
        String instruction = "";
        String newLine = "";
        boolean out = false;
        boolean commentFlag = false;
        boolean header = false;
        while ((line = reader.readLine()) != null) {
            statement:
            if (line.startsWith("#")) {
                String tempLine = line;
                tempLine = tempLine.replaceFirst("#", "");

                if (checkForInstruction(tempLine)) {
                    boolean outCommInstruction = true;
                    Comment c = new Comment(snapshot, "commented out: " + getInstructionInString(tempLine), tempLine);
                    comments.add(c);
                    break statement;
                }
                if (commentFlag) {
                    String concatComment = line.replaceFirst("#", " ");
                    newLine += concatComment;
                } else {
                    newLine += line;
                    commentFlag = true;
                }
            } else if (line.trim().isEmpty()) {
                if (commentFlag && header) {
                    Comment c = new Comment(snapshot, "standalone", newLine);
                    comments.add(c);
                } else if (commentFlag && !header) {
                    Comment c = new Comment(snapshot, "header", newLine);
                    comments.add(c);
                    header = true;
                }
                newLine = "";
                instruction = "";
                commentFlag = false;

            } else if (doesLineHaveAnInstruction(line) && commentFlag) {
                if (line.contains("\t")) {
                    String uname = " ";
                    line = line.replaceAll("\t", uname);
                }
                String arr[] = line.split(" ", 2);

                String foundInstruction = arr[0];

                Comment c = new Comment(snapshot, "before " + foundInstruction, newLine);
                comments.add(c);

                newLine = "";
                instruction = "";
                commentFlag = false;
            }
        }
        reader.close();
        fis.close();

        if (comments.size() > 0) {
            return comments;
        }
        return comments;
    }

    public File getFlatDockerFile(File dockerFile) throws IOException {

       
        File flatDockerfile = new File(dockerFile.getParentFile().getPath() + "/DockerFileFlat");
        //System.out.println(flatDockerfile);
        BufferedWriter writer = new BufferedWriter(new FileWriter(flatDockerfile));
        FileInputStream fis = new FileInputStream(dockerFile);
        BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
        String line = null;
        String newLine = null;
        boolean hc = false;
        boolean concatFlag = false;
        while ((line = reader.readLine()) != null) {
            //System.out.println(line);
               
            if (doesLineHaveAnInstruction(line) && line.contains(" \\")) {

                if(!concatFlag)
                {
                    newLine = "";
                }

                newLine += line;
                concatFlag = true;
            } else if (line.contains(" \\") && concatFlag) {
                newLine += line;
            } else if (newLine != null && newLine.contains("HEALTHCHECK") && !hc) {
                newLine += line;
                hc = true;
            } else if (!doesLineHaveAnInstruction(line) && !line.contains(" \\") && concatFlag) {
                newLine += line;
                newLine = newLine.replace(" \\", "");
                newLine = newLine.trim().replaceAll(" +", " ");
                writer.write(newLine);
                writer.newLine();
                concatFlag = false;
            } else if (doesLineHaveAnInstruction(line) && !line.contains(" \\")) {
                line = line.trim().replaceAll(" +", " ");
                writer.write(line);
                writer.newLine();
            }
        }
        writer.close();
        reader.close();
        fis.close();

        return flatDockerfile;
    }

    public File getFlatDockerFile(String localRepoPath, String localDockerfilePath) throws IOException {
        String localPathtoDockerfile = localRepoPath.concat("/").concat(localDockerfilePath);
        File dockerfile = findFile("Dockerfile", new File(localPathtoDockerfile));
        return getFlatDockerFile(dockerfile);
    }

    public File getFlatDockerFile(String fileName) throws IOException {
        String localPathtoDockerfile = localPath.concat("/").concat("/").concat(localDockerfilePath);
        File dockerfile = findFile(fileName, new File(localPathtoDockerfile));
        return getFlatDockerFile(dockerfile);
    }

    public static String getInstructionInString(String line) {
        String lineU = line.toUpperCase();
        if (lineU.contains(" ADD ") | line.matches("^ADD .*")) {
            return "ADD";
        } else if (lineU.contains(" FROM ") | line.matches("^FROM .*")) {
            return "FROM";
        } else if (lineU.contains(" CMD ") | line.matches("^CMD .*")) {
            return "CMD";
        } else if (lineU.contains(" COPY")| line.matches("^COPY .*")) {
            return "COPY";
        } else if (lineU.contains(" ENTRYPOINT ")| line.matches("^ENTRYPOINT .*")) {
            return "ENTRYPOINT";
        } else if (lineU.contains(" ENV ")| line.matches("^ENV .*")) {
            return "ENV";
        } else if (lineU.contains(" EXPOSE ")| line.matches("^EXPOSE .*")) {
            return "EXPOSE";
        } else if (lineU.contains(" FROM ")| line.matches("^FROM .*")) {
            return "FROM";
        } else if (lineU.contains(" HEALTHCHECK ")| line.matches("^HEALTHCHECK .*")) {
            return "HEALTHCHECK";
        } else if (lineU.contains(" INSTRUCTION ")| line.matches("^INSTRUCTION .*")) {
            return "INSTRUCTION";
        } else if (lineU.contains(" LABEL ")| line.matches("^LABEL .*")) {
            return "LABEL";
        } else if (lineU.contains(" MAINTAINER ")| line.matches("^MAINTAINER .*")) {
            return "MAINTAINER";
        } else if (lineU.contains(" ONBUILD ")| line.matches("^ONBUILD .*")) {
            return "ONBUILD";
        } else if (lineU.contains(" RUN ")| line.matches("^RUN .*")) {
            return "RUN";
        } else if (lineU.contains(" STOPSIGNAL ")| line.matches("^STOPSIGNAL .*")) {
            return "STOPSIGNAL";
        } else if (lineU.contains(" USER ")| line.matches("^USER .*")) {
            return "USER";
        } else if (lineU.contains(" VOLUME ")| line.matches("^VOLUME .*")) {
            return "VOLUME";
        } else if (lineU.contains(" WORKDIR ")| line.matches("^WORKDIR .*")) {
            return "WORKDIR";
        } else {
            return "";
        }
    }

    public static boolean doesLineHaveAnInstruction(String line) {
        String lineU = line.toUpperCase();
        if (lineU.contains(" ADD ") | line.matches("^ADD .*")) {
            return true;
        } else if (lineU.contains(" FROM ") | line.matches("^FROM .*")) {
            return true;
        } else if (lineU.contains(" CMD ") | line.matches("^CMD .*")) {
            return true;
        } else if (lineU.contains(" COPY")| line.matches("^COPY .*")) {
            return true;
        } else if (lineU.contains(" ENTRYPOINT ")| line.matches("^ENTRYPOINT .*")) {
            return true;
        } else if (lineU.contains(" ENV ")| line.matches("^ENV .*")) {
            return true;
        } else if (lineU.contains(" EXPOSE ")| line.matches("^EXPOSE .*")) {
            return true;
        } else if (lineU.contains(" FROM ")| line.matches("^FROM .*")) {
            return true;
        } else if (lineU.contains(" HEALTHCHECK ")| line.matches("^HEALTHCHECK .*")) {
            return true;
        } else if (lineU.contains(" INSTRUCTION ")| line.matches("^INSTRUCTION .*")) {
            return true;
        } else if (lineU.contains(" LABEL ")| line.matches("^LABEL .*")) {
            return true;
        } else if (lineU.contains(" MAINTAINER ")| line.matches("^MAINTAINER .*")) {
            return true;
        } else if (lineU.contains(" ONBUILD ")| line.matches("^ONBUILD .*")) {
            return true;
        } else if (lineU.contains(" RUN ")| line.matches("^RUN .*")) {
            return true;
        } else if (lineU.contains(" STOPSIGNAL ")| line.matches("^STOPSIGNAL .*")) {
            return true;
        } else if (lineU.contains(" USER ")| line.matches("^USER .*")) {
            return true;
        } else if (lineU.contains(" VOLUME ")| line.matches("^VOLUME .*")) {
            return true;
        } else if (lineU.contains(" WORKDIR ")| line.matches("^WORKDIR .*")) {
            return true;

        } else if (lineU.startsWith("#")) {
            return true;
        } else {
            return false;
        }
    }

    public void doClassificationOfLines(File file) throws IOException {
        try (Stream<String> lines = Files.lines(file.toPath(), Charset.defaultCharset())) {
            lines.forEachOrdered(line -> {
                
                String instruction = getInstructionInString(line);
                
                if(doesLineHaveAnInstruction(line)){
                    if ((isSingleInstruction(instruction))) {
                            getInstructionOneOfOne(line);
                    } else {
                        
                        if(areMultipleInstructionsInOneLineAllowed(line)){
                            System.out.println("Lines: " + line + instruction);
                            getMultipleInstructionsInOneLine(line);
                        }else{
                            getInstructionOneOfMany(line);
                        }
                    }
                }


            });
        }
    }

    private boolean areMultipleInstructionsInOneLineAllowed(String instructionToCheck) {
        //String instructionToCheckU = instructionToCheck.toUpperCase();
        String instructionToCheckU = instructionToCheck;
        if (instructionToCheckU.contains("ENV")) {
            return false;
        } else if (instructionToCheckU.contains("ADD")) {
            return false;
        } else if (instructionToCheckU.contains("COPY")) {
            return false;
        } else if (instructionToCheckU.contains("USER")) {
            return false;
        } else if (instructionToCheckU.contains("WORKDIR")) {
            return false;
        } else if (instructionToCheckU.contains("ARG")) {
            return false;
        } else if (instructionToCheckU.contains("ONBUILD")) {
            return false;
        } else {
            return true;
        }
    }

    public boolean isSingleInstruction(String instructionToCheck) {
        String instructionToCheckU = instructionToCheck.toUpperCase();
        if (instructionToCheckU.contains("FROM")) {
            return true;
        } else if (instructionToCheckU.contains("CMD")) {
            return true;
        } else if (instructionToCheckU.contains("ENTRYPOINT")) {
            return true;
        } else if (instructionToCheckU.contains("HEALTHCHECK")) {
            return true;
        } else if (instructionToCheckU.contains("STOPSIGNAL")) {
            return true;
        } else {
            return false;
        }
    }


    public <T extends Instruction> T getInstructionOneOfOne(String newLine) {
        String line = newLine;
        if (line.startsWith("#")) {
            return null;
        }

        if (line.contains("\t")) {
            String uname = " ";
            line = line.replaceAll("\t", uname);
        }

        String arr[] = line.split(" ", 2);

        String instruction = arr[0];   //Instruction
        String command;
        if (arr.length > 1) {
            command = arr[1];
        } else {
            command = "";
        }

        switch (instruction.toUpperCase()) {
            case "FROM":
                return (T) parseFromInstruction(command);
            case "CMD":
                return (T) (parseAndGetCMDInstruction(command));
            case "ENTRYPOINT":
                return (T) (parseAndGetEntryPointInstruction(command));
            case "STOPSIGNAL":
                return (T) (parseAndGetStopSignalInstruction(command));
            //case "HEALTHCHECK":
            //    return (T) (parseAndGetHealthCheckInstruction(command));
        }
        return null;
    }

    public <T extends Instruction> List<T> getMultipleInstructionsInOneLine(String newLine) {
        String line = newLine;
        if (line.startsWith("#")) {
            return null;
        }

        if (line.contains("\t")) {
            String uname = " ";
            line = line.replaceAll("\t", uname);
        }

        String arr[] = line.split(" ", 2);

        String instruction = arr[0];   //Instruction
        String command;
        if (arr.length > 1) {
            command = arr[1];
        } else {
            command = "";
        }

        
        switch (instruction.toUpperCase()) {
            case "RUN":
                return (List<T>) parseAndGetRunInstruction(command);
            case "EXPOSE":
                return (List<T>) parseAndGetExposeInstruction(command);
            //case "LABEL":
            //    return (List<T>) parseAndGetLabelInstruction(command);
            case "VOLUME":
                return (List<T>) parseAndGetVolumeInstruction(command);
        }
        return null;
    }

    public <T extends Instruction> T getInstructionOneOfMany(String newLine) {
        String line = newLine;
        if (line.startsWith("#")) {
            return null;
        }

        if (line.contains("\t")) {
            String uname = " ";
            line = line.replaceAll("\t", uname);
        }

        String arr[] = line.split(" ", 2);

        String instruction = arr[0];   //Instruction
        String command;
        if (arr.length > 1) {
            command = arr[1];
        } else {
            command = "";
        }

        switch (instruction.toUpperCase()) {
            case "ENV":
                return (T) parseAndGetEnvInstruction(command);
            case "ADD":
                return (T) parseAndGetAddInstruction(command);
            case "COPY":
                return (T) parseAndGetCopyInstruction(command);
            case "USER":
                return (T) parseAndGetUserInstruction(command);
            case "WORKDIR":
                return (T) parseAndGetWorkDirInstruction(command);
            case "ARG":
                return (T) parseAndGetArgInstruction(command);
            case "ONBUILD":
                return (T) parseAndGetOnBuildInstruction(command);
        }
        return null;
    }

    public List<Expose> parseAndGetExposeInstruction(String ports) {
        List<Expose> exposes = new ArrayList<>();
        
        if (ports.matches(".*\\d+.*")) {
            
            String[] parts = ports.split(" ");
            

            for (int i = 0; i < parts.length; i++) {
                
                if (parts[i].matches(".*:.*")) {
               
                    String bindingPort = parts[0].replaceAll(":.*","");
                    exposes.add(new Expose(dockerfile, bindingPort));
    
                } else if (parts[i].matches(".*-.*")){
                    String bindingPort = parts[0].replaceAll("-.*","");
                    exposes.add(new Expose(dockerfile, bindingPort));
                } else if (parts[i].matches(".*,.*")){
                    String bindingPort = parts[0].replaceAll(",.*","");
                    exposes.add(new Expose(dockerfile, bindingPort));
                } else if (parts[i].matches(".*#.*")){
                    String bindingPort = parts[0].replaceAll("#.*","");
                    exposes.add(new Expose(dockerfile, bindingPort));
                } else if (parts[i].matches(".*\t.*")){
                    String bindingPort = parts[0].replaceAll("\t.*","");
                    exposes.add(new Expose(dockerfile, bindingPort));
                }
            }
        }
        if (exposes.size() != 0) {
            this.exposes = exposes;
            return exposes;
        }
        return null;
    }

    public Instruction parseAndGetEntryPointInstruction(String command) {
        String executable = null;
        List<String> params = new ArrayList<>();
        if (command.contains("[")) {
            Pattern p = Pattern.compile("\"(.*?)\"");
            Matcher m = p.matcher(command);

            List<String> matches = new ArrayList<String>();
            while (m.find()) {
                matches.add(m.group(1));
            }

            for (int i = 0; i < matches.size(); i++) {
                if (i == 0) {
                    executable = matches.get(i);
                } else {
                    params.add(matches.get(i));
                }
            }
            if (executable == null) {
                return null;
            }

        } else {
            String[] parts = command.split(" ");

            for (int i = 0; i < parts.length; i++) {
                if (i == 0) {
                    executable = parts[i];

                } else {
                    params.add(parts[i]);
                }
            }

        }
        if (executable.length() > 0) {
            entryPoint = new EntryPoint(dockerfile, executable, params);
        }
        return entryPoint;

    }

    public Add add(String command, String instruction) {
        String source = "";
        String destinatation = "";
        if (command.contains("[")) {
            Pattern p = Pattern.compile("\"(.*?)\"");
            Matcher m = p.matcher(command);

            List<String> matches = new ArrayList<String>();
            while (m.find()) {
                matches.add(m.group(1));
            }
            for (int i = 0; i < matches.size(); i++) {
                if (i == 0) {
                    source = matches.get(i);
                } else {
                    destinatation = matches.get(i);
                }
            }
        } else {
            String[] parts = command.split(" ");
            for (int i = 0; i < parts.length; i++) {
                if (i == 0) {
                    source = parts[i];

                } else {
                    destinatation = parts[i];
                }
            }
        }

        if (instruction.toUpperCase().equals("ADD")) {
            adds.add(new Add(dockerfile, source, destinatation));
            return new Add(dockerfile, source, destinatation);
        }
        return null;
    }

    public Copy copy(String command, String instruction) {
        String source = "";
        String destinatation = "";
        if (command.contains("[")) {
            Pattern p = Pattern.compile("\"(.*?)\"");
            Matcher m = p.matcher(command);

            List<String> matches = new ArrayList<String>();
            while (m.find()) {
                matches.add(m.group(1));
            }
            for (int i = 0; i < matches.size(); i++) {
                if (i == 0) {
                    source = matches.get(i);
                } else {
                    destinatation = matches.get(i);
                }
            }
        } else {
            String[] parts = command.split(" ");
            for (int i = 0; i < parts.length; i++) {
                if (i == 0) {
                    source = parts[i];

                } else {
                    destinatation = parts[i];
                }
            }
        }
        if (instruction.toUpperCase().equals("COPY")) {
            copies.add(new Copy(dockerfile, source, destinatation));
            return new Copy(dockerfile, source, destinatation);
        }
        return null;
    }

    public Copy parseAndGetCopyInstruction(String command) {
        return copy(command, "COPY");

    }

    public Add parseAndGetAddInstruction(String command) {
        return add(command, "ADD");
    }

    public Instruction parseAndGetWorkDirInstruction(String path) {
        workDirs.add(new WorkDir(dockerfile, path));
        return new WorkDir(dockerfile, path);
    }

    public List<Label> parseAndGetLabelInstruction(String command) {
        System.out.println(command);
        List<Label> labels = new ArrayList<>();
        String[] parts = command.split(" ",2);

        System.out.println(parts);

        for (int i = 0; i < parts.length; i++) {
            String value = null;
            String key = null; 
            String[] split = null;

            
            if (parts[i].contains("=")) {

                split = parts[i].split("=");
                key = split[0];
                value = split[1];

            } else if (parts.length >= 2){

                key = parts[0];
                value = parts[1];

            }

            Pattern p = Pattern.compile("\"(.*?)\"");
            Matcher m = p.matcher(value);

            while (m.find()) {
                value = m.group(1);
            }

            labels.add(new Label(dockerfile, key, value));
        }
        this.labels = labels;
        return labels;
    }

    public Instruction parseAndGetHealthCheckInstruction(String command) {
        Healthcheck hc = null;
        String instruction = getInstructionInString(command);

        String[] split = command.split(" ", 2);

        if (command.startsWith(instruction)) {
            hc = new Healthcheck(dockerfile, instruction, split[1]);
        } else {
            int index = command.indexOf(instruction);
            String paramter = command.substring(0, index-1);
            String fullInstruction = command.substring(index);
            String[] fullInstructions = fullInstruction.split(" ", 2);
            hc = new Healthcheck(dockerfile, instruction, paramter, fullInstructions[1]);
        }
        healthcheck = hc;
        return hc;
    }

    public Instruction parseAndGetCMDInstruction(String command) {
        String executable = null;
        List<String> params = new ArrayList<>();
        if (command.contains("[")) {
            Pattern p = Pattern.compile("\"(.*?)\"");
            Matcher m = p.matcher(command);

            List<String> matches = new ArrayList<String>();
            while (m.find()) {
                matches.add(m.group(1));
            }

            for (int i = 0; i < matches.size(); i++) {
                if (i == 0) {
                    executable = matches.get(i);
                } else {
                    params.add(matches.get(i));
                }
            }
        } else {
            String[] parts = command.split(" ");

            for (int i = 0; i < parts.length; i++) {
                if (i == 0) {
                    executable = parts[i];

                } else {
                    params.add(parts[i]);
                }
            }

        }
        if (executable == null) {
            String[] parts = command.split(" ");

            for (int i = 0; i < parts.length; i++) {
                if (i == 0) {
                    executable = parts[i];

                } else {
                    params.add(parts[i]);
                }
            }
        } else {
            if (executable.length() > 0) {
                cmd = new Cmd(dockerfile, executable, params);
            }
        }

        return cmd;
    }

    public Instruction parseAndGetStopSignalInstruction(String signal) {
        stopSignal = new StopSignal(dockerfile, signal);

        return stopSignal;


    }

    public Instruction parseAndGetArgInstruction(String arg) {
        args.add(new Arg(dockerfile, arg));
        return new Arg(dockerfile, arg);

    }

    public List<Run> parseAndGetRunInstruction(String commandx) {

        boolean runOverMulipleLines = false;

        List<Run> runsLocal = new ArrayList<>();
        String command = commandx;
        if (commandx.contains("(") && !commandx.contains("echo ")) {
            // Find all commands in parentehesis
            Matcher m = Pattern.compile("\\(([^)]+)\\)").matcher(commandx);
            while (m.find()) {
                command = m.group(1);
            }
        }

        //if (command.contains("\\$")) {
        //    String content = Files.readString(path, StandardCharsets.UTF_8);
        //    runOverMulipleLines = true;
        //}

        
        System.out.println("Ich bin der run" + commandx);
        String[] runentry = command.split(" && ");
        

        for (String run : runentry) {
            String executable = "";
            List<String> params = new ArrayList<>();

            if (run.contains("echo")) {
                executable = "echo";
                String arr[] = run.split(" ", 2);
                int i = 0;

                if (arr[0].equals("echo")){
                    i = 1;
                }

                for (; i < arr.length; i++) {
                    params.add(arr[i]);
                }

            } else {
                if (command.contains("[")) {
                    Pattern p = Pattern.compile("\"(.*?)\"");
                    Matcher m = p.matcher(command);

                    List<String> matches = new ArrayList<String>();
                    while (m.find()) {
                        matches.add(m.group(1));
                    }

                    for (int i = 0; i < matches.size(); i++) {
                        if (i == 0) {
                            executable = matches.get(i);
                        } else {
                            params.add(matches.get(i));
                        }
                    }
                } else {
                    String[] parts = run.split(" ");

                    for (int i = 0; i < parts.length; i++) {
                        if (i == 0) {
                            executable = parts[i];

                        } else {
                            params.add(parts[i]);
                        }
                    }

                }
            }
            if (executable.length() > 0) {
                runsLocal.add(new Run(dockerfile, executable, params));
                runs.add(new Run(dockerfile, executable, params));
            }
        }
        return runsLocal;
    }


    public List<Volume> parseAndGetVolumeInstruction(String command) {
        List<Volume> volumes = new ArrayList<>();
        if (command.contains("[")) {
            Pattern p = Pattern.compile("\"(.*?)\"");
            Matcher m = p.matcher(command);

            List<String> matches = new ArrayList<String>();
            while (m.find()) {
                matches.add(m.group(1));
            }

            for (String match :
                    matches) {
                volumes.add(new Volume(dockerfile, match));
            }
        } else {
            String[] parts = command.split(" ");
            for (int i = 0; i < parts.length; i++) {
                volumes.add(new Volume(dockerfile, parts[i]));
            }
        }
        this.volumes = volumes;
        return volumes;
    }

    public Instruction parseAndGetOnBuildInstruction(String command) {
        OnBuild onBuild = null;
        String instruction = getInstructionInString(command);

        String[] split = command.split(" ", 2);
        if (checkForInstruction(split[0]) && split.length > 0) {
            onBuild = new OnBuild(dockerfile, instruction, split[1]);
        }
        onBuilds.add(onBuild);
        return onBuild;
    }

    public Instruction parseAndGetUserInstruction(String command) {
        users.add(new User(dockerfile, command));
        return new User(dockerfile, command);


    }

    public Instruction parseAndGetShellInstruction(String command) {
        return null;
    }

    public Instruction parseAndGetMaintainerInstruction(String user) {
        maintainer = new Maintainer(dockerfile, user);
        return new Maintainer(dockerfile, user);
    }

    public Env parseAndGetEnvInstruction(String command) {
        String replaced = command.replaceAll("\\s+", " ");
        String[] parts = replaced.split(" ");
        String key = parts[0];
        String value = "";
        if (parts.length > 1) {
            value = parts[1];
        }
        envs.add(new Env(dockerfile, key, value));
        return new Env(dockerfile, key, value);
    }

    public Instruction parseFromInstruction(String command) {
        if (command.contains("/")) {
            from = new From(dockerfile, command);
            return from;
        } else if (command.contains("@")) {
            String[] parts = command.split("@");
            String image = parts[0];
            String imageVersion = parts[1];
            from = new From(dockerfile, image, imageVersion, "digest");
            return from;
        } else if (command.split("\\w+").length == 0) {
            from = new From(dockerfile, command);
            return from;
        } else if (command.matches(".*\\d+.*")) {
            Double imageVersion = Double.parseDouble(getStringFromRegexPattern("(?:\\d*\\.)?\\d+", command));
            String[] parts = command.split(":");
            String image = parts[0];
            from = new From(dockerfile, image, imageVersion);
            return from;
        } else if (command.matches("\\w*(:)\\w+")) {
            String[] parts = command.split(":");
            String image = parts[0];
            String imageVersion = parts[1];
            from = new From(dockerfile, image, imageVersion, "imageVersion");
            return from;
        }
        return from;
    }


    public String getStringFromRegexPattern(String patternInput, String data) {
        Pattern pattern = Pattern.compile(patternInput);
        Matcher matcher = pattern.matcher(data);
        try {
            if (matcher.find()) {
                //  System.out.println("getStringFromRegexPattern" + matcher.group(0));
            }
            return matcher.group(0);
        } catch (Exception e) {
            // e.printStackTrace();
            return "";
        }
    }

    public File findFile(String name, File path) {
        File[] list = path.listFiles();
        if (list != null)
            for (File file : list) {
                if (file.isDirectory()) {
                    findFile(name, file);
                } else if (name.equalsIgnoreCase(file.getName())) {
                    return file;
                }
            }
        return null;
    }

    public void assignToDockerObject(File file) throws IOException {
        dockerfile.comments = getCommentsFromDockerfile(file, dockerfile);
        dockerfile.from = from;
        dockerfile.maintainer = maintainer;
        dockerfile.cmd = cmd;
        dockerfile.entryPoint = entryPoint;
        dockerfile.stopSignals = stopSignal;
        dockerfile.healthCheck = healthcheck;

        dockerfile.runs = this.runs;

        dockerfile.labels = this.labels;
        dockerfile.envs = this.envs;

        dockerfile.exposes = this.exposes;
        dockerfile.adds = this.adds;
        dockerfile.copies = this.copies;
        dockerfile.volumes = this.volumes;
        dockerfile.users = this.users;
        dockerfile.workDirs = this.workDirs;
        dockerfile.args = this.args;
        dockerfile.onBuilds = this.onBuilds;
    }

   /* @Override
    public String toString() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setVisibility(JsonMethod.FIELD, JsonAutoDetect.Visibility.ANY);
        String json = null;
        try {
            //Convert object to JSON string and save into file directly
            mapper.writeValue(new File(localPath + "/" + "dockerfile.json"), dockerfile);

            //Convert object to JSON string
            json = mapper.writeValueAsString(dockerfile);

        } catch (JsonGenerationException e) {
            e.printStackTrace();
        } catch (JsonMappingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonParser jp = new JsonParser();
        JsonElement je = jp.parse(json);
        String prettyJsonString = gson.toJson(je);

        String header = "*************************************************** \n";

        String footer = "*************************************************** \n";
        return header + prettyJsonString + footer;
    }

    public void printDockerfile(DockerfileSnapshot dockerfileSnapshot) {
        System.out.println(this.toString());

    }*/
}