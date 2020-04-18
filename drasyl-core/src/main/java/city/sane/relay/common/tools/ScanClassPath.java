/*
 * Copyright (c) 2020
 *
 * This file is part of Relayserver.
 *
 * Relayserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Relayserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Relayserver.  If not, see <http://www.gnu.org/licenses/>.
 */

package city.sane.relay.common.tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import city.sane.relay.common.models.Pair;

/**
 * Scans the given paths for classes containing certain features
 * 
 * @author wp
 */
@SuppressWarnings({ "rawtypes", "unchecked", "squid:CommentedOutCodeLine" })
public class ScanClassPath {

    private static final Logger LOG = LoggerFactory.getLogger(ScanClassPath.class);

    private Set<String> classes = new HashSet<>();

    /**
     * Returns file-valid class identifiers found on the classpath with matching
     * annotation
     */
    public Set<String> getClassesPath() {
        return classes;
    }

    /**
     * Returns valid class identifiers found on the classpath with matching
     * annotation
     */
    public Set<String> getClasses() {
        return classes.parallelStream().map(s -> s.replace("/", ".")).collect(Collectors.toSet());
    }

    /**
     * Retrieves all registered URLs using various ClassLoaders
     */
    public List<URL> getRootUrls() {
        List<URL> result = new ArrayList<>();

        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        while (cl != null) {
            if (cl instanceof URLClassLoader) {
                URL[] urls = ((URLClassLoader) cl).getURLs();
                result.addAll(Arrays.asList(urls));
            }
            cl = cl.getParent();
        }
        return result;
    }

    /**
     * Loads a class from the specified path and its annotation instance
     * 
     * @param <T>        some {@link Annotation}
     * @param classpath  java class, e.g.: <i>"java.lang.String"</i>
     * @param annotation the annotation to retrieve
     * @return the loaded class as {@link Class} and instantiated annotation,
     *         returns <code>null</code> if nothing worked, returns
     *         Pair(class,<code>null</code>) if annotation not found
     */
    public <T extends Annotation> Pair<Class, T> loadClassAndGetAnnotation(String classpath, Class<T> annotation) {
        try {
            Class<?> loadClass = ClassLoader.getSystemClassLoader().loadClass(classpath);
            if (loadClass.getAnnotation(annotation) != null) {
                Annotation value = loadClass.getAnnotation(annotation);
                return Pair.of(loadClass, (T) value);
            } else {
                return Pair.of(loadClass, null);
            }
        } catch (ClassNotFoundException e) {
            LOG.info("Could deal with class {}", classpath, e);
        }
        return null;
    }

    /**
     * Scan for annotations using the URLs provided by various ClassLoaders
     * 
     * @param annotation
     * @param suffix     suffix of class name, if not needed enter <code>null</code>
     * @param prefix     prefix of class name, if not needed enter <code>null</code>
     * @throws IOException
     */
    public void scanForAnnotation(Class annotation, String suffix, String prefix) throws IOException {
        String annotationInClass = "L" + annotation.getName().replace(".", "/") + ";";

        for (URL url : getRootUrls()) {
            File f = new File(url.getPath());
            if (f.isDirectory()) {
                LOG.info("Scanning file {} for Annotation", url.getPath());
                visitFile(f, annotationInClass, suffix, prefix);
            } else {
                LOG.info("Scanning jar {} for Annotation", url.getPath());
                visitJar(url, annotationInClass, suffix, prefix);
            }
        }
    }

    /**
     * Scan for annotations from the java.class.path variable
     * 
     * @param annotation what to look for
     * @param suffix     suffix of class name, if not needed enter <code>null</code>
     * @param prefix     prefix of class name, if not needed enter <code>null</code>
     * @throws IOException when reading files fails
     */
    public void scanForAnnotationSystemPath(Class annotation, String suffix, String prefix) throws IOException {
        String[] classPathEntries = System.getProperty("java.class.path").split(File.pathSeparator);
        scanForAnnotation(annotation, classPathEntries, suffix, prefix);
    }

    /**
     * Scans for annotation on the given paths-array
     * 
     * @param annotation what to look for
     * @param paths      where to look
     * @param suffix     suffix of class name, if not needed enter <code>null</code>
     * @param prefix     prefix of class name, if not needed enter <code>null</code>
     * @throws IOException when reading files fails
     */
    public void scanForAnnotation(Class annotation, String[] paths, String suffix, String prefix) throws IOException {
        String annotationInClass = "L" + annotation.getName().replace(".", "/") + ";";

        for (String stringUrl : paths) {
            File f = new File(stringUrl);
            URL url = f.toURI().toURL();
            if (f.isDirectory()) {
                visitFile(f, annotationInClass, suffix, prefix);
            } else {
                visitJar(url, annotationInClass, suffix, prefix);
            }
        }
    }

    private void visitFile(File f, String annotationCheck, String suffix, String prefix) throws IOException {
        if (f.isDirectory()) {
            final File[] children = f.listFiles();
            if (children != null) {
                for (File child : children) {
                    visitFile(child, annotationCheck, suffix, prefix);
                }
            }
        } else if (f.getName().endsWith(".class")) {
            try (FileInputStream in = new FileInputStream(f)) {
                handleClass(in, annotationCheck, suffix, prefix);
            }
        }
    }

    private void visitJar(URL url, String annotationCheck, String suffix, String prefix) throws IOException {
        try (JarFile jarFile = new JarFile(url.getFile())) {

            final Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                final JarEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".class")) {
                    JarEntry fileEntry = jarFile.getJarEntry(entry.getName());

                    try (InputStream input = jarFile.getInputStream(fileEntry)) {
                        handleClass(input, annotationCheck, suffix, prefix);
                    }
                }
            }
        } catch (NoSuchFileException | FileNotFoundException e) {
            LOG.warn("Couldn't load a jar file at {}", url);
        }
    }

    private void handleClass(InputStream in, String annotationCheck, String suffix, String prefix) throws IOException {
        try {
            AnnotationClassVisitor cv = new AnnotationClassVisitor(annotationCheck, suffix, prefix);

            ClassReader classReader = new ClassReader(in);
            classReader.accept(cv, 0);

            if (cv.hasAnnotation && cv.hasPrefix && cv.hasSuffix) {
                classes.add(classReader.getClassName());
            }
        } catch (UnsupportedOperationException e) {
            LOG.warn("ignoring nested class for {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            LOG.warn("ignoring class file major version due to mismatch {}", e.getMessage());
        }
    }

    private class AnnotationClassVisitor extends ClassVisitor {
        private boolean hasSuffix = true;
        private boolean hasPrefix = true;
        private boolean hasAnnotation;
        private final String annotationReference;
        private final String suffix;
        private final String prefix;

        AnnotationClassVisitor(String annotationRefName, String suffix, String prefix) {
            super(Opcodes.ASM7);
            this.annotationReference = annotationRefName;
            this.suffix = suffix;
            this.prefix = prefix;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
                String[] interfaces) {
            //NOSONAR
            String className = name.replace('/', '.');
            if (suffix != null)
                hasSuffix = name.endsWith(suffix);
            if (prefix != null) {
                String[] classPathParts = className.split(Pattern.quote("."));
                hasPrefix = classPathParts[classPathParts.length - 1].startsWith(prefix);
            }
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            if (Objects.equals(desc, annotationReference)) {
                hasAnnotation = true;
            }
            return null;
        }
    }

}
