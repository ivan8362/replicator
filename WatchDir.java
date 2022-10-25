package org.example;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;

public class WatchDir {
    private final WatchService watcher;
    private final Map<WatchKey,Path> keys;
    private final boolean recursive;
    private boolean trace = false;
    @SuppressWarnings("unchecked")
    static <T> WatchEvent<T> cast(WatchEvent<?> event) {
        return (WatchEvent<T>)event;
    }
    /**
     * Creates a WatchService and registers the given directory
     */
    WatchDir(Path dir, boolean recursive) throws IOException {
        this.watcher = FileSystems.getDefault().newWatchService();
        this.keys = new HashMap<WatchKey,Path>();
        this.recursive = recursive;

        if (recursive) {
            System.out.format("Scanning %s ...\n", dir);
            registerAll(dir);
            System.out.println("Done.");
        } else {
            register(dir);
        }

        // enable trace after initial registration
        this.trace = true;
    }
    void watchDirectoryPath(int sourceCount, Path destination) {
        try {
            // Start the infinite polling loop
            WatchKey key = null;
            while (true) {
                key = watcher.take();

                Path dir = keys.get(key);
                if (dir == null) {
                    System.err.println("WatchKey not recognized!");
                    continue;
                }
                // Dequeueing events
                WatchEvent.Kind<?> kind = null;
                for (WatchEvent<?> watchEvent : key.pollEvents()) {
                    // Get the type of the event
                    kind = watchEvent.kind();
                    if (OVERFLOW == kind) {
                        continue; // loop
                    } else if (ENTRY_CREATE == kind) {
                        // A new Path was created
                        Path newPath = ((WatchEvent<Path>) watchEvent).context();
                        // Output
                        System.out.println("New path created: " + newPath);
                        Path child = dir.resolve(newPath);
                        if (recursive) {
                            try {
                                if (Files.isDirectory(child, NOFOLLOW_LINKS)) {
                                    registerAll(child);
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        int count = child.getNameCount();
                        Path relativeTar = child.subpath(sourceCount, count);
                        Path forTarget = destination.resolve(relativeTar);
                        if (Files.isDirectory(child)) {
                            if (!Files.exists(forTarget)) {
                                Files.createDirectory(forTarget);
                            }
                        } else {
                            // regular file
                            FileUtil.copyDirectory(child.toFile(), forTarget.toFile());
                        }
                    } else if (ENTRY_MODIFY == kind) {
                        // modified
                        Path newPath = ((WatchEvent<Path>) watchEvent).context();
                        // Output
                        System.out.println("New path modified: " + newPath);

                        Path child = dir.resolve(newPath);
                        int count = child.getNameCount();
                        Path relativeTar = child.subpath(sourceCount, count);
                        Path forTarget = destination.resolve(relativeTar);
                        Files.delete(forTarget);
                        FileUtil.copyDirectory(child.toFile(), forTarget.toFile());
                    } else if (ENTRY_DELETE == kind) {
                        Path newPath = ((WatchEvent<Path>) watchEvent).context();
                        System.out.println("a path was removed: " + newPath);
                        Path child = dir.resolve(newPath);
                        int count = child.getNameCount();
                        Path relativeTar = child.subpath(sourceCount, count);
                        Path forTarget = destination.resolve(relativeTar);
                        FileUtil.delete(forTarget.toFile());
                    }
                }
                boolean valid = key.reset();
                if (!valid) {
                    keys.remove(key);

                    // all directories are inaccessible
                    if (keys.isEmpty()) {
                        break;
                    }
                }
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        } catch (InterruptedException ie) {
            ie.printStackTrace();
        }
    }
    private void register(Path dir) throws IOException {
        WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        if (trace) {
            Path prev = keys.get(key);
            if (prev == null) {
                System.out.format("register: %s\n", dir);
            } else {
                if (!dir.equals(prev)) {
                    System.out.format("update: %s -> %s\n", prev, dir);
                }
            }
        }
        keys.put(key, dir);
    }
    private void registerAll(final Path start) throws IOException {
        // register directory and sub-directories
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException
            {
                register(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
