package org.basex.core.cmd;

import static org.basex.core.Text.*;

import java.io.*;
import java.util.*;

import org.basex.core.*;
import org.basex.data.*;
import org.basex.io.*;
import org.basex.io.out.*;
import org.basex.io.serial.*;
import org.basex.util.*;
import org.basex.util.list.*;

/**
 * Evaluates the 'export' command and saves the currently opened database
 * to disk.
 *
 * @author BaseX Team 2005-12, BSD License
 * @author Christian Gruen
 */
public final class Export extends Command {
  /**
   * Default constructor, specifying a target path.
   * @param path export path
   */
  public Export(final String path) {
    super(Perm.CREATE, true, path);
  }

  @Override
  protected boolean run() {
    try {
      final Data data = context.data();
      export(data, args[0]);
      return info(DB_EXPORTED_X, data.meta.name, perf);
    } catch(final IOException ex) {
      Util.debug(ex);
      return error(ex.getMessage());
    }
  }

  /**
   * Exports the current database to the specified path.
   * Files and directories in {@code path} will be possibly overwritten.
   * @param data data reference
   * @param target directory
   * @throws IOException I/O exception
   */
  public static void export(final Data data, final String target)
      throws IOException {

    final String exp = data.meta.prop.get(Prop.EXPORTER);
    final SerializerProp sp = new SerializerProp(exp);
    final IOFile root = new IOFile(target);
    root.md();

    final HashSet<String> exported = new HashSet<String>();

    // export XML documents
    final IntList il = data.resources.docs();
    if(!data.isEmpty()) {
      for(int i = 0, is = il.size(); i < is; i++) {
        final int pre = il.get(i);
        // create file path
        final IO file = root.merge(Token.string(data.text(pre, true)));
        // create dir if necessary
        final IOFile dir = new IOFile(file.dir());
        if(!dir.exists()) dir.md();

        // serialize file
        final PrintOutput po = new PrintOutput(unique(exported, file.path()));
        final Serializer ser = Serializer.get(po, sp);
        ser.node(data, pre);
        ser.close();
        po.close();
      }
    }

    // export raw files
    final IOFile bin = data.meta.binaries();
    for(final String s : bin.descendants()) {
      final String u = unique(exported, new IOFile(root.path(), s).path());
      new IOFile(bin.path(), s).copyTo(new IOFile(u));
    }
  }

  /**
   * Returns a unique file path.
   * @param exp exported names
   * @param fp file path
   * @return unique path
   */
  private static String unique(final HashSet<String> exp, final String fp) {
    int c = 1;
    String path = fp;
    while(exp.contains(path)) {
      path = fp.indexOf('.') == -1 ? fp + '(' + ++c + ')' :
           fp.replaceAll("(.*)\\.(.*)", "$1(" + ++c + ").$2");
    }
    exp.add(path);
    return path;
  }
}
