package ru.fizteh.fivt.students.vlasov_vadim.filemap;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.RandomAccessFile;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.util.*;

public class Filemap {

    static class ExitException extends Exception {
        private static final long serialVersionUID = 1L;
    }

    private static boolean interactiveMode;
    
    private static Path filePath;
    private static TreeMap<String, String> fileMap;

    public static void main(final String[] args) {
        try {
            linkFile();
            if (args.length == 0) {
                interactiveMode = true;
            }
            if (interactiveMode) {
                beginInteractiveMode();
            } else {
                beginCmdMode(args);
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
            try (RandomAccessFile dbFile
                = new RandomAccessFile(filePath.toString(), "rw")) {
                putData(dbFile, fileMap);
            } 
            catch (Exception e1) {
                System.err.println("Error writing to file");
            }
            System.exit(-1);
        }
    }
    
    private static void linkFile() throws ExitException {
        fileMap = new TreeMap<>();
        try {
            filePath = Paths.get(System.getProperty("db.file"));
            try (RandomAccessFile dbFile
                    = new RandomAccessFile(filePath.toString(), "r")) {
                if (dbFile.length() > 0) {
                    getData(dbFile, fileMap);
                }
            } catch (FileNotFoundException e) {
                filePath.toFile().createNewFile();
            }
        } catch (Exception e) {
            System.err.println("Data base file could not be found or created");
            throw new ExitException();
        }
    }

    private static void getData(//2nd variant
            RandomAccessFile dbFile, TreeMap<String, String> fileMap)
            throws IOException {
        
        List<String> keys = new LinkedList<>();
        List<Integer> offsets = new LinkedList<>();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte b;
        int counter = 0;
        int firstOffset = 0;
        
        do { 
            while ((b = dbFile.readByte()) > 0) { 
                counter++;
                bytes.write(b);
            }
            if (firstOffset == 0) { 
                firstOffset = dbFile.readInt();
            } else {
                offsets.add(dbFile.readInt());
            }
            counter += 5; // 1 ('\0') + 4 (int);
            keys.add(bytes.toString("UTF-8"));
            bytes.reset();
        } 
        while (counter < firstOffset);
        
        offsets.add((int) dbFile.length());
        Iterator<String> keyIterator = keys.iterator();
        for (Integer currentOffset : offsets) {
            while (currentOffset > counter) {
                bytes.write(dbFile.readByte());
                counter++;
            }
            if (bytes.size() > 0) {
                fileMap.put(keyIterator.next(), bytes.toString("UTF-8"));
                bytes.reset();
            } else {
                throw new IOException();
            }
        }
        bytes.close();
    }

    private static void putData(//2nd variant
            RandomAccessFile dbFile, TreeMap<String, String> fileMap)
            throws IOException {
        
        dbFile.setLength(0);
        Set<String> keys = fileMap.keySet();
        List<Integer> reserved = new LinkedList<>();
        List<Integer> offsets = new LinkedList<>();
        
        for (String currentKey : keys) {
            dbFile.write(currentKey.getBytes("UTF-8"));
            dbFile.write('\0');
            reserved.add((int) dbFile.getFilePointer());
            dbFile.writeInt(0);
        }
        
        for (String currentKey : keys) { 
            offsets.add((int) dbFile.getFilePointer());
            dbFile.write(fileMap.get(currentKey).getBytes("UTF-8"));
        }
        
        Iterator<Integer> offsetIterator = offsets.iterator();
        for (int offsetPos : reserved) { 
            dbFile.seek(offsetPos);
            dbFile.writeInt(offsetIterator.next());
        }
    }
    
    private static void beginCmdMode(final String[] args) {
        StringBuilder commandline = new StringBuilder();
        for (String arg : args) {
            commandline.append(arg);
            commandline.append(' ');
        }
        String[] commands = (commandline.toString()).trim().split(";");
        execCommands(commands);
    }

    private static void beginInteractiveMode() {
        try (Scanner s = new Scanner(System.in)) {
            while (true) {
                System.out.print("$ ");
                String line = s.nextLine();
                String[] commands = (line.toString()).trim().split(";");
                execCommands(commands);
            }
        }
    }

    public static void execCommands(final String[] commands) {
        try {
            for (String command : commands) {
                execute(command);
            }
        } catch (ExitException e) {
            System.exit(0);
        }
    }

    private static void execute(final String commandline) throws ExitException {
        String commandlinetrimmed = commandline.trim();
        final String[] commands = commandlinetrimmed.split("\\s+");
        try {
            switch (commands[0]) {
            case "put":
                put(commands);
                break;
            case "get":
                get(commands);
                break;
            case "remove":
                remove(commands);
                break;
            case "list":
                list(commands);
                break;
            case "exit":
                exit(commands);
                break;
            default:
                System.err.println(commands[0] + ": invalid command");
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
            if (!interactiveMode) {
                System.exit(-1);
            }
        }
    }

    private static void put(final String[] args) {
        if (args.length != 3) {
            throw new IllegalArgumentException("put: wrong number of arguments");
        }
        String s = fileMap.put(args[1], args[2]);
        if (s != null) {
            System.out.println("overwrite");
            System.out.println(s);
        } else {
            System.out.println("new");
        }
    }

    private static void get(final String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("get: wrong number of arguments");
        }
        String s = fileMap.get(args[1]);
        if (s != null) {
            System.out.println("found");
            System.out.println(s);
        } else {
            System.out.println("not found");
        }
    }

    private static void remove(final String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("remove: wrong number of arguments");
        }
        String s = fileMap.remove(args[1]);
        if (s != null) {
            System.out.println("removed");
        } else {
            System.out.println("not found");
        }
    }

    private static void list(final String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("list: wrong number of arguments");
        }
        Set<String> keys = fileMap.keySet();
        int counter = 0;
        for (String current : keys) {
            ++counter;
            System.out.print(current);
            if (counter != keys.size()) {
                System.out.print(", ");
            }
        }
        System.out.println();
    }
    
    
    private static void exit(final String[] args) throws ExitException {
        if (args.length != 1) {
            System.err.println("exit: wrong number of arguments");
        }
        throw new ExitException();
    }
}
