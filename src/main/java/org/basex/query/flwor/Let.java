package org.basex.query.flwor;

import static org.basex.query.QueryText.*;
import static org.basex.util.Token.*;
import java.io.IOException;

import org.basex.io.serial.Serializer;
import org.basex.query.*;
import org.basex.query.expr.Expr;
import org.basex.query.item.Bln;
import org.basex.query.item.Dbl;
import org.basex.query.item.Item;
import org.basex.query.item.SeqType;
import org.basex.query.item.Value;
import org.basex.query.iter.Iter;
import org.basex.query.util.*;
import org.basex.query.var.*;
import org.basex.util.InputInfo;
import org.basex.util.ft.Scoring;

/**
 * Let clause.
 *
 * @author BaseX Team 2005-12, BSD License
 * @author Christian Gruen
 */
public final class Let extends ForLet {
  /** Scoring flag. */
  final boolean score;

  /**
   * Constructor.
   * @param ii input info
   * @param e variable input
   * @param v variable
   */
  public Let(final InputInfo ii, final Expr e, final Var v) {
    this(ii, e, v, false);
  }

  /**
   * Constructor.
   * @param ii input info
   * @param e variable input
   * @param v variable
   * @param s score flag
   */
  public Let(final InputInfo ii, final Expr e, final Var v, final boolean s) {
    super(ii, e, v);
    score = s;
  }

  @Override
  public Let comp(final QueryContext ctx, final VarScope scp) throws QueryException {
    expr = checkUp(expr, ctx).comp(ctx, scp);
    type = SeqType.ITEM;
    size = var.size = expr.size();
    var.refineType(score ? SeqType.DBL : expr.type(), info);
    return this;
  }

  @Override
  public Iter iter(final QueryContext ctx) {
    return new Iter() {
      /** Iterator flag. */
      private boolean more;

      @Override
      public Item next() throws QueryException {
        if(!more) {
          final Value v;
          if(score) {
            // assign average score value
            double s = 0;
            int c = 0;
            final Iter ir = ctx.iter(expr);
            for(Item it; (it = ir.next()) != null;) {
              s += it.score();
              ++c;
            }
            v = Dbl.get(Scoring.let(s, c));
          } else {
            v = ctx.value(expr);
          }
          ctx.set(var, v, info);
          more = true;
          return Bln.TRUE;
        }
        reset();
        return null;
      }

      @Override
      public long size() {
        return 1;
      }

      @Override
      public Item get(final long i) throws QueryException {
        reset();
        return next();
      }

      @Override
      public boolean reset() {
        if(more) more = false;
        return true;
      }
    };
  }

  @Override
  boolean simple(final boolean one) {
    return !score;
  }

  @Override
  public void plan(final Serializer ser) throws IOException {
    ser.openElement(this, score ? token(SCORE) : VAR, token(var.toString()));
    expr.plan(ser);
    ser.closeElement();
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder(LET).append(' ');
    if(score) sb.append(SCORE).append(' ');
    sb.append(var).append(' ').append(ASSIGN).append(' ').append(expr);
    return sb.toString();
  }

  @Override
  public boolean declares(final Var v) {
    return var.is(v);
  }

  @Override
  public Var[] vars() {
    return new Var[]{ var };
  }

  /** {@inheritDoc} This method doesn't undeclare the variables. */
  @Override
  public boolean visitVars(final VarVisitor visitor) {
    return expr.visitVars(visitor) && visitor.declared(var);
  }
}
