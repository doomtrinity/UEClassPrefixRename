# Unreal Engine C++ class rename tool  
by doomtrinity

### A simple command line program to change the prefix of C++ classes for Unreal Engine projects.  

Usage: java -jar UEClassPrefixRename.jar "`<sourceDir`>" `<oldPrefix`> `<newPrefix`> `<projectName`> `<ignorelistFile`>  
Example:  
java -jar UEClassPrefixRename.jar "/home/doomtrinity/test/Source" Shooter Fps ShooterGame “/home/doomtrinity/test/ignorelist.txt”  

This program will both rename files and refactor source code.  
A “new” folder containing the refactored source files will be created in the same path of the source parent folder, leaving original files unaffected. Also, a list of redirects to be used in DefaultEngine.ini will be printed to the console.  
For example, if you have a class named ShooterCharacter (which inherits from Actor class), and you want to rename “Shooter” prefix with “Fps”, this tool will:  
- rename ShooterCharacter.h to FpsCharacter.h
- rename ShooterCharacter.cpp to FpsCharacter.cpp
- refactor class AShooterCharacter to AFpsCharacter and replace all references in all source files
- print the redirects to be used in DefaultEngine.ini so blueprints and references to this class in the engine will still work as before  
This tool has been designed to leave other “Shooter” words, which are not class names, unaffected.
Say you have:  
AShooterCharacter* ShooterCharacter = Cast`<AShooterCharacter`>(GetPawn());  
You’ll get:  
AFpsCharacter* ShooterCharacter = Cast`<AFpsCharacter`>(GetPawn());  
  
Renaming classes could be a dangerous thing and it’s very easy to get into troubles in doing so, especially in Unreal Engine and for many classes at once. Perhaps that’s why I didn’t find any tool for this, or perhaps I reinvented the wheeI. I wanted to keep things simple for my needs, so the code is pretty crude.  
##### Use this at your own risk! 
  
###### Rules:  
- `<sourceDir`> parameter in command line options must be the absolute path of the source folder and has to be included in double quotes
this is fine: “c:\users\doomtrinity\MyUnrealProject\Source”  
this is fine: “/home/user/doomtrinity/MyUnrealProject/Source”  
this is bad: “Source”  
this is bad: “c:\users\doomtrinity\MyUnrealProject”  
- after "new" folder has been created by the program, it has to be removed in order to run the program again in the same path
- all command line params must be non-empty except `<ignorelistFile`> which is optional
- old prefix and new prefix cannot contain spaces - these are part of .cpp & .h file name and class so the same rule for name applies here
- source files should obey few coding standards which I'm quite sure are the same ones Epic proposes.
- the name of the class definition in the header file (plus additional Unreal prefixes such as A or U) must be placed on the same line of the "class" keyword. This program is not intended to be a C++ file parser, but I needed to add few checks on strings like this in order to get what I wanted.  
E.g. in MyActor.h, this is fine:  
class AMyActor : public AActor // class name in same line of “class” keyword, ok!  
and this is bad  
class  
           AMyActor : public AActor // new line, bad!  
this is needed by the program in order to detects classes to be renamed. Also, the name of .h file must be the same of .cpp file.   Files that don’t fulfill these rules will just simply be copied without being renamed, still these files could be refactored if references to other renamed classes are in there.
- `<ignorelistFile`> is an absolute path to a simple text file where you have to put all headers of the classes you want to ignore, one per line, e.g.  
ShooterGame.h  
ShooterEngine.h  
  
###### Important notes:
- character encoding has not be handled so if there are special characters (e.g. language-related) in the source file e.g. in comments, these may be corrupted after file refactoring. Technically it’d be possible to set the character encoding through \-Dfile.encoding command line opt but I haven't tested that.
- only names of header files which contain the class definition with the same name of the file (plus additional Unreal prefix such as A or U) will be considered for refactoring, so a simple header with only definitions in it, won’t be renamed (still it could be refactored, if contains references to other classes to be renamed) 
- a tool to detect changes (e.g. the one in git) would help to see what has been updated
- if something goes wrong, an exception will be thrown. I didn’t bother to make pretty messages but you should get the idea of what went wrong. Remember to throw away or move the “new” folder to run the program again.
- a list of all classes that will be renamed is printed right before redirects for DefaultEngine.ini
- there are few cases where this program won’t replace the class name in source files. The case I faced is in ShooterCheatManager.h at line:  
UCLASS(Within=ShooterPlayerController)  
I intentionally left this out, you’ll get a compile error, at least in this case, so you’ll know about it. The problem here is that the name is the file name and not the class name which may contain Unreal prefixes. I only look for file names in \#include, and I look  for class names in the remaining part of the code, so this is an exception.
-this program is designed to replace prefix, but it should also work if the word you need to replace is in the middle of the class name or at the end, I just haven’t tested it.
- in case of projects derived from templates like ShooterGame, this old project name will still be referenced in few places even if you assign a different name when you create the project from the launcher. You have to use this name instead of your project name, as `<projectName`> parameter. Otherwise you should use your project name, for projects created from scratch. Project rename is not covered here.
- projects like ShooterGame could be tricky to rename as there are many files and references involved, so you cannot just use this program and hope to get all classes renamed and a working game. You’ll get into troubles for sure, at least that happened to me when I tried to build a packaged version of the game. This is where ignorelist file will help. You should put in this file any file that deserves special treatment and couldn’t just be renamed with a batch tool. You will find the file I used for my test in resources folder.
- `<projectName`> is used by the program only as part of redirects for DefaultEngine.ini.
- I tested this program with the ShooterGame sample project by Epic, in Unreal Engine 4.19.2 (editor and packaged Win 64bit build), without getting any error
- bear in mind you cannot just copy and paste redirects to DefaultEngine.ini, as there might already be some redirects of same classes (which have to be deleted as we’ll use new ones) and/or other references to be updated in that file, like GameModeClassAliases
- you need to throw away old generated files and build the game from scratch. I usually throw away Intermediate, Binaries folders and MyProject.VC.db, MyProject.sln file. This is needed to recreate generated.h files with updated names. You obviously need to delete the old “Source” folder too and paste the new one, then right click on .uproject and generate new solution files

###### Requirements
Java Runtime Environment 1.8
