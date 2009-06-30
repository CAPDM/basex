/**
 * 
 */
package org.basex.build.fs.parser;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.basex.BaseX;

/**
 * Some utility methods for loading class files from folders, packages or jar
 * files.
 * @author Bastian Lemke
 */
public final class Loader extends ClassLoader {

  /** The ClassLoader instance to use for loading classes. */
  private static final Loader INSTANCE = new Loader();

  /** Hidden constructor. */
  private Loader() { /* */}

  /**
   * Load some classes from a package that is on the classpath.
   * @param pkg the package to load the classes from.
   * @param fileNamePattern the pattern used for matching the class names.
   * @return an array with all (binary) names of the loaded classes (e.g.
   *         org.basex.build.fs.parser.MP3Parser). All available classes from
   *         the package are listed here, if if they are not loaded (because
   *         they were loaded before).
   */
  public static String[] load(final Package pkg, //
      final Pattern fileNamePattern) {
    String pkgName = pkg.getName();
    String pkgPath = pkgName.replace('.', '/');

    Matcher matcher;
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    URL pkgUrl = cl.getResource(pkgPath);
    File packageDir = new File(pkgUrl.getPath());
    ArrayList<String> loadedClasses = new ArrayList<String>();
    for(File f : packageDir.listFiles()) {
      String fileName = f.getName();
      fileName = fileName.substring(0, fileName.length() - 6);
      matcher = fileNamePattern.matcher(fileName);
      if(matcher.matches()) {
        String clazz = pkgName + "." + fileName;
        try {
          Class.forName(clazz);
        } catch(ClassNotFoundException e) {
          // should never occur since the package is on the classpath and we
          // checked that the file exists.
          BaseX.errln("Failed to load class (%)", e.getMessage());
          break;
        }
        loadedClasses.add(clazz);
      }
    }
    return loadedClasses.toArray(new String[loadedClasses.size()]);
  }

  /**
   * <p>
   * Load some classes from the given directory that is possibly not on the
   * classpath.
   * </p>
   * <p>
   * There may be restrictions for the usage of this classes (e.g. if they
   * extend an abstract class that was loaded before with a different
   * {@link ClassLoader}). <b>Classes that were loaded with a different
   * {@link ClassLoader} may not be able to access fields or methods from these
   * classes!</b>
   * </p>
   * <p>
   * Whenever possible, use {@link #load(Package, Pattern)} instead of this
   * method to avoid problems.
   * </p>
   * @param directory the directory to load the classes from.
   * @param fileNamePattern the pattern used for matching the class names. All
   *          (sub)subclasses (e.g. MP3Parser$Frame and MP3Parser$Frame$1) must
   *          be included in the pattern.
   * @return an array with all (binary) names of the loaded classes (e.g.
   *         org.basex.build.fs.parser.MP3Parser). All available classes from
   *         the package are listed here, if if they are not loaded (because
   *         they were loaded before).
   * @throws IOException if one of the classes could not be read.
   */
  public static String[] load(final File directory,
      final Pattern fileNamePattern) throws IOException {
    Matcher matcher;
    ArrayList<Class<?>> foundClasses = new ArrayList<Class<?>>();
    ArrayList<File> subClasses = new ArrayList<File>();
    ArrayList<File> subSubClasses = new ArrayList<File>();
    for(File f : directory.listFiles()) {
      String fileName = f.getName();
      fileName = fileName.substring(0, fileName.length() - 6);
      matcher = fileNamePattern.matcher(fileName);
      if(matcher.matches()) {
        // hack to detect (sub)subclasses that must be loaded after the classes
        if(fileName.contains("$")) {
          if(fileName.indexOf('$') != fileName.lastIndexOf('$')) {
            subSubClasses.add(f);
            continue;
          }
          subClasses.add(f);
          continue;
        }
        Class<?> c = load(f);
        foundClasses.add(c);
      }
    }
    // load subclasses
    for(File f : subClasses)
      load(f);
    // load subsubclasses
    for(File f : subSubClasses)
      load(f);
    // initialize classes
    int counter = 0;
    for(Class<?> c : foundClasses) {
      try {
        Class.forName(c.getName(), true, INSTANCE);
        counter++;
      } catch(ClassNotFoundException e) {
        // should never occur since the package is on the classpath and we
        // checked that the file exists.
        BaseX.errln("Failed to load class (%)", e.getMessage());
        break;
      }
    }
    return foundClasses.subList(0, counter).toArray(new String[counter]);
  }

  /**
   * Reads the class object from the file.
   * @param f the file to read from.
   * @return the (uninitialized) class object.
   * @throws IOException if any error occurs while reading from the file.
   */
  private static Class<?> load(final File f) throws IOException {
    long len = f.length();
    if(len > Integer.MAX_VALUE) throw new IOException(
        "Class file too long to load.");
    byte[] buf = new byte[(int) f.length()];
    FileChannel ch = new RandomAccessFile(f, "r").getChannel();
    ch.read(ByteBuffer.wrap(buf));
    ch.close();
    return INSTANCE.defineClass(null, buf, 0, buf.length);
  }
}
