package org.basex.core.proc;

import static org.basex.Text.*;
import static org.basex.core.Commands.*;
import java.io.IOException;
import org.basex.core.Process;
import org.basex.io.PrintOutput;

/**
 * Evaluates the 'help' command.
 *
 * @author Workgroup DBIS, University of Konstanz 2005-08, ISC License
 * @author Christian Gruen
 */
public final class Help extends Process {
  /**
   * Constructor.
   * @param o option
   */
  public Help(final String o) {
    super(PRINTING, o);
  }

  @Override
  protected void out(final PrintOutput out) throws IOException {
    try {
      out.print(COMMANDS.valueOf(args[0]).help(true, true));
    } catch(final Exception ex) {
      out.println(CMDHELP);
      final boolean all = ALL.equalsIgnoreCase(args[0]);
      for(final COMMANDS c : COMMANDS.values()) out.print(c.help(false, all));
    }
  }
}
