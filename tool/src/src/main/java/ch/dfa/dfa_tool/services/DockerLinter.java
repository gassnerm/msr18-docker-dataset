package ch.dfa.dfa_tool.services;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
/**
 * Created by salizumberi-laptop on 26.10.2016.
 */
public class DockerLinter {

    public final static String HADOLINT_EXEC = "docker run --rm -i hadolint/hadolint < ";
    public static String getReportOfLinting(File file) throws IOException, InterruptedException{
        String filePath = file.getAbsolutePath().replaceAll("null/", "");
        String exec  = HADOLINT_EXEC + filePath;
        ProcessBuilder processBuilder = null;
        //"sed -i -e 's/.*#.*//' " + filePath + " && " + "sed -i -e '/^$/d' " + filePath  + " && " + "sed -i -e 's#^\u0009##' " + filePath + " && " + "sed -i -e 's#^\u0020##' " + filePath + " && " + 
        processBuilder = new ProcessBuilder("/bin/bash","-c",exec);

        //Path filePath = Path.of(file.getAbsolutePath());
        //String content = Files.readString(filePath);
        //content.replaceAll("#.*\n","");
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        StringBuilder processOutput = new StringBuilder();
        
        try (BufferedReader processOutputReader = new BufferedReader(
                new InputStreamReader(process.getInputStream())))
        {
            String readLine;
            while ((readLine = processOutputReader.readLine()) != null){
                processOutput.append(readLine + System.lineSeparator());
            }
            process.waitFor();
        } 
        System.out.println(processOutput.toString());
        return processOutput.toString().trim();
    }
}