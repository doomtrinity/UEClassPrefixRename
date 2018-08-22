/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ueclassprefixrename;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 *
 * @author doomtrinity
 */
public class UEClassPrefixRename {
    
    static final String[] UNREAL_PREFIXES = {"A","U","S","F"};

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws IOException {
        
        //args = new String[]{"/home/doomtrinity/Desktop/Test/Source","Shooter","Fps","ShooterGame","/home/doomtrinity/Desktop/Test/ignorelist.txt"};
        
        if(args == null || args.length < 4){
            printUsage();
            return;
        }
        
        for(String arg : args){
            if(arg == null || arg.trim().isEmpty()){
                printUsage(); 
                return;
            }
        }

        final String sourceDir = args[0];
        final String oldPrefix = args[1];
        final String newPrefix = args[2];
        final String projectName = args[3];
        final String ignorelistFilePath;
        if(args.length > 4) {
            ignorelistFilePath = args[4];
        } else {
            ignorelistFilePath = null;
        }
        
        final OldNewPrefix prefix = new OldNewPrefix(oldPrefix,newPrefix);

        final List<OldNewName> oldNewNames = new ArrayList<>();
        
        // source dir validation
        final File sourceFolder = new File(sourceDir);
        if((sourceFolder.exists() 
                && sourceFolder.isDirectory() 
                && sourceFolder.getName().equals("Source")
            ) == false){
            throw new IOException("Cannot locate source dir in path: "+sourceDir);
        }
        
        // ignorelist file validation
        if(ignorelistFilePath != null) {
            final File ignorelistFile = new File(ignorelistFilePath);
            if((ignorelistFile.exists() && !ignorelistFile.isDirectory()) == false){
                throw new IOException("Cannot locate ignorelist file: "+ignorelistFilePath);
            }
        }
        
        final String parentDir = sourceFolder.getParent();
        final File newSourceFolder = new File(parentDir,"new//Source");
        if(newSourceFolder.mkdirs() == false){
            throw new IOException("Cannot create new source directory. Does it already exist?");
        }

        // create list of classes to be renamed
        try (Stream<Path> paths = Files.walk(Paths.get(sourceDir)).filter((path) -> path.toString().endsWith(".h"))) {
            final File[] files = paths.map(p -> p.toFile()).toArray(File[]::new);
            final List<String> classesToIgnore = ignorelistFilePath != null ? Files.readAllLines(Paths.get(ignorelistFilePath)) : new ArrayList<>(0);
            System.out.println("/////////////////////////////////////////");
            System.out.println("Name changes detected:");
            System.out.printf("%-32s %32s %16s\n","OLD","NEW","UNREAL PREFIX");
            for(File file : files){    
                if(classesToIgnore.contains(file.getName())){
                    continue;
                }
                if(file.getName().contains(oldPrefix) == false){ // should be startsWith but there are few cases where that will be false e.g. SShooterMenuItem in ShooterGame project
                    continue;
                }
                final String unrealPrefix = OldNewName.getUnrealClassPrefix(file);
                if(unrealPrefix != null){
                    oldNewNames.add(new OldNewName(file,prefix,unrealPrefix));
                }                
            }            
        } catch (IOException e) {
            Logger.getLogger(UEClassPrefixRename.class.getName()).log(Level.SEVERE, null, e);
            throw e;
        }
        
        // refactor source files body with new classes names
        try (Stream<Path> paths = Files.walk(Paths.get(sourceDir)).filter((path) -> path.toString().endsWith(".h") || path.toString().endsWith(".cpp"))) {
            final File[] files = paths.map(p -> p.toFile()).toArray(File[]::new);
            for(File file : files){
                // cache file lines
                final List<String> newLines = new ArrayList<>();
                try(BufferedReader br = new BufferedReader(new FileReader(file))) {
                    final List<String> oldLines = new ArrayList<>();                    
                    for(String line; (line = br.readLine()) != null; ) {  
                        //System.out.println(line);
                        oldLines.add(line);
                    }     
                    // foreach line replace class name
                    for(String line : oldLines){
                        if(line.contains(prefix.oldPrefix)){                          
                            for(OldNewName name : oldNewNames){
                                line = replaceClassName(name, line);
                                if(line.contains(prefix.oldPrefix) == false){
                                    break;
                                }
                            }
                        }
                        newLines.add(line); 
                    }
                }
                
                // write new lines to file
                final String relativeDir = file.getAbsolutePath().substring(sourceDir.length());
                final File destFileParentDir = (new File(newSourceFolder,relativeDir)).getParentFile();
                if(destFileParentDir.exists() == false && destFileParentDir.mkdirs() == false){
                    throw new IOException("Cannot create dir "+destFileParentDir.getAbsolutePath());
                }
                final File destFile = new File(destFileParentDir,file.getName());
                try(BufferedWriter bw = new BufferedWriter(new FileWriter(destFile))) {
                    for(String line : newLines){
                       //System.out.println(line);
                       bw.write(line);
                       bw.newLine();
                    }
                }               
            }
        } catch (IOException e) {
            Logger.getLogger(UEClassPrefixRename.class.getName()).log(Level.SEVERE, null, e);
            throw e;
        }
        
        // rename files
        try (Stream<Path> paths = Files.walk(Paths.get(newSourceFolder.getAbsolutePath()))
                .filter((path) -> path.toString().endsWith(".h") || path.toString().endsWith(".cpp"))) {
            final File[] files = paths.map(p -> p.toFile()).toArray(File[]::new);
            System.out.println("/////////////////////////////////////////");
            System.out.println("Redirects for DefaultEngine.ini:");
            for(File file : files){
                final String oldFileNameNoExt = file.getName().split("\\.")[0];
                if(oldNewNames.stream().anyMatch(n -> oldFileNameNoExt.equals(n.oldName))){ // some files with prefix may not have related class e.g. header with definitions only. Skip them.
                   final String newFileName = file.getName().replace(oldPrefix, newPrefix);
                   final String newFileNameNoExt = newFileName.split("\\.")[0];
                   if(file.renameTo(new File(file.getParent(),newFileName)) == false){
                       throw new IOException("Cannot rename "+file.getAbsolutePath());
                   }
                   if(newFileName.endsWith(".h")){
                       System.out.println(
                           String.format("+ActiveClassRedirects=(OldClassName=\"/Script/%s.%s\",NewClassName=\"/Script/%s.%s\")",
                                projectName,oldFileNameNoExt,
                                projectName,newFileNameNoExt)
                        );
                   }                   
                }
            }
        } catch (IOException e) {
            Logger.getLogger(UEClassPrefixRename.class.getName()).log(Level.SEVERE, null, e);
            throw e;
        }
        
        // copy other files that are neither .h not .cpp to new
        try (Stream<Path> paths = Files.walk(Paths.get(sourceFolder.getAbsolutePath()))
                .filter((path) -> !path.toString().endsWith(".h") && !path.toString().endsWith(".cpp") && !Files.isDirectory(path))) {
            final File[] files = paths.map(p -> p.toFile()).toArray(File[]::new);
            for(File file : files){
                final String relativeDir = file.getAbsolutePath().substring(sourceDir.length());
                final File destFileParentDir = (new File(newSourceFolder,relativeDir)).getParentFile();
                if(destFileParentDir.exists() == false && destFileParentDir.mkdirs() == false){
                    throw new IOException("Cannot create dir "+destFileParentDir.getAbsolutePath());
                }
                final File destFile = new File(destFileParentDir,file.getName());
                if(destFile.createNewFile() == false){
                    throw new IOException("Cannot create dest file "+destFile.getAbsolutePath());
                }                
                try(FileInputStream fis = new FileInputStream(file);
                        FileOutputStream fos = new FileOutputStream(destFile)) {
                    byte[] buffer = new byte[1024];
                    int noOfBytes;
                    while ((noOfBytes = fis.read(buffer)) != -1) {
                        fos.write(buffer, 0, noOfBytes);
                    }
                }
                
            }
        } catch (IOException e) {
            Logger.getLogger(UEClassPrefixRename.class.getName()).log(Level.SEVERE, null, e);
            throw e;
        }
        
    }
    
    public static void printUsage(){
        System.out.println("/////////////////////////////////////////");
        System.out.println("Usage: java -jar UEClassPrefixRename.jar \"<SourceDir>\" <oldPrefix> <newPrefix> <projectName> \"<ignorelistFile>\"");
        System.out.println("<SourceDir>: absolute path of Source folder");
        System.out.println("<oldPrefix>: prefix to be updated");
        System.out.println("<newPrefix>: target prefix");
        System.out.println("<projectName>: project name that will be used to create redirects for DefaultEngine.ini");
        System.out.println("[OPTIONAL] <ignorelistFile>: absolute path of file containing a list of headers with .h extension of classes to ignore, one per line");
        System.out.println("Example:");
        System.out.println("java -jar UEClassPrefixRename.jar \"/home/test/Source\" Shooter Fps ShooterGame \"/home/test/ignorelist.txt\"");
        System.out.println("/////////////////////////////////////////");
    }
    
    public static String replaceClassName(OldNewName name, String line){
        final String oldName;
        final String newName;
        if(line.contains("#include")){ // FIXME, should also check for UCLASS(Within=Foo), where Foo is the name of the file
            // return line; // testing without rename file name
            oldName = name.oldName;
            newName = name.newName;
        } else {
            oldName = name.getOldClassName();
            newName = name.getNewClassName();            
        }
        
        while(OldNewName.containsValidClassName(oldName,line)){ // beware of infinite loop if old name and new name are equals but this is actually forbidden from start 
            final int replaceFromIndex = OldNewName.getClassNameIndex(oldName,line);
            final String preName = line.substring(0, replaceFromIndex);
            final String postName = line.substring(replaceFromIndex+oldName.length());
            final String result = preName + newName + postName;
            line = result;
        }
        return line;
    }
    
    public static class OldNewPrefix {
        
        final String oldPrefix;
        final String newPrefix;
        
        public OldNewPrefix (String oldPrefix, String newPrefix){
            if(oldPrefix == null || oldPrefix.isEmpty()){
                throw new IllegalArgumentException("invalid oldPrefix" + oldPrefix);
            }
            if(newPrefix == null || newPrefix.isEmpty()){
                throw new IllegalArgumentException("invalid newPrefix" + newPrefix);
            }
            this.oldPrefix = oldPrefix;
            this.newPrefix = newPrefix;
        }
    }
    
    public static class OldNewName {
        
        final File file;
        final OldNewPrefix prefix;
        final String oldName;
        final String newName;
        final String unrealPrefix;  
        
        public OldNewName(File file, OldNewPrefix prefix, String unrealPrefix) throws IOException{
            if(file.exists() == false){
                throw new IllegalArgumentException("invalid path" + file.getCanonicalPath());
            }
            if(file.getName().contains(prefix.oldPrefix) == false){
                throw new IllegalStateException("file name "+ file.getCanonicalPath() +" does not contain prefix "+ prefix.oldPrefix);
            }
            if(prefix == null){
                throw new IllegalArgumentException("invalid prefix");
            }
            if(unrealPrefix == null){
                throw new IllegalArgumentException("invalid unrealPrefix");
            }
            
            this.file = file;
            this.prefix = prefix;
            
            this.oldName = file.getName().split("\\.")[0];
            this.newName = this.oldName.replace(prefix.oldPrefix, prefix.newPrefix);
            
            if(this.oldName.equals(this.newName)){
                throw new IllegalStateException("new name cannot be like old name: "+this.oldName);
            }
            
            this.unrealPrefix = unrealPrefix;
            
            System.out.printf("%-32s %32s %16s\n",oldName,newName,unrealPrefix);            
        }
        
        public String getOldClassName(){
            return unrealPrefix+oldName;
        }
        
        public String getNewClassName(){
            return unrealPrefix+newName;
        }

        @Override
        public String toString() {
            return "old name=" + oldName + ", new name=" + newName +", unreal class prefix=" + unrealPrefix;
        }
        
        public static String getUnrealClassPrefix(File file) throws IOException { // use to check if .h contains class
            try(BufferedReader br = new BufferedReader(new FileReader(file))) {
                final String possibleClasses[] = new String [UNREAL_PREFIXES.length];
                final String className = file.getName().split("\\.")[0];
                for(int i=0; i<UNREAL_PREFIXES.length;i++){
                    final String classNameWithPrefix = UNREAL_PREFIXES[i]+className;
                    possibleClasses[i] = classNameWithPrefix;
                }   
                for(String line; (line = br.readLine()) != null; ) {
                    if((line.trim().startsWith("#include") == false 
                            && line.contains("class")) == false ){ // FIXME, very basic check for class declaration
                        continue;
                    }
                    for(String uclass : possibleClasses){
                        if(containsValidClassName(uclass,line)){
                            return uclass.substring(0, 1);
                        }
                    }
                    if(containsValidClassName(className,line)){
                        return "";
                    }
                }                
            }
            return null;
        }
        
        public static boolean containsValidClassName(String className, String line){ 
            return getClassNameIndex(className, line) != -1;
        }
        
        // returns -1 if not valid
        private static int getClassNameIndex(String className, String line, int startIndex){
            if(line.indexOf(className,startIndex) != -1){
                final int nextIndex = line.indexOf(className,startIndex)+className.length();
                if(line.length() <= nextIndex){
                    return line.indexOf(className);
                }
                final char next = line.charAt(nextIndex);
                if(Character.isLetter(next) || Character.isDigit(next) || next == '_'){
                    return getClassNameIndex(className,line,nextIndex);
                }    
                return line.indexOf(className,startIndex);
            }
            return -1;
        }
        
        public static int getClassNameIndex(String className, String line){
            return getClassNameIndex(className,line,0);
        }        
        
    }
    
}
