package org.basex.query.gflwor;

import java.io.*;

import static org.basex.query.QueryText.*;

import org.basex.io.serial.*;
import org.basex.query.*;
import org.basex.query.expr.*;
import org.basex.query.gflwor.GFLWOR.Eval;
import org.basex.query.item.Dbl;
import org.basex.query.item.Empty;
import org.basex.query.item.Int;
import org.basex.query.item.Item;
import org.basex.query.iter.Iter;
import org.basex.query.util.*;
import org.basex.query.var.*;
import org.basex.util.*;

/**
 * FLWOR {@code for} clause, iterating over a sequence.
 *
 * @author BaseX Team 2005-12, BSD License
 * @author Leo Woerteler
 */
public class For extends GFLWOR.Clause {
  /** Item variable. */
  final Var var;
  /** Position variable. */
  final Var pos;
  /** Score variable. */
  final Var score;
  /** Bound expression. */
  Expr expr;
  /** {@code allowing empty} flag. */
  final boolean empty;

  /**
   * Constructor.
   * @param v item variable
   * @param p position variable or {@code null}
   * @param s score variable or {@code null}
   * @param e bound expression
   * @param emp {@code allowing empty} flag
   * @param ii input info
   */
  public For(final Var v, final Var p, final Var s, final Expr e, final boolean emp,
      final InputInfo ii) {
    super(ii, vars(v, p, s));
    var = v;
    pos = p;
    score = s;
    expr = e;
    empty = emp;
  }

  @Override
  Eval eval(final Eval sub) {
    return new Eval() {
      /** Expression iterator. */
      private Iter iter;
      /** Current position. */
      private long p;
      @Override
      public boolean next(final QueryContext ctx) throws QueryException {
        while(true) {
          final Item it = iter == null ? null : iter.next();
          if(it != null) {
            // there's another item to serve
            ctx.set(var, it, input);
            if(pos != null) ctx.set(pos, Int.get(++p), input);
            if(score != null) ctx.set(score, Dbl.get(it.score()), input);
            return true;
          } else if(empty && iter != null && p == 0) {
            // expression yields no items, bind the empty sequence instead
            ctx.set(var, Empty.SEQ, input);
            if(pos != null) ctx.set(pos, Int.get(p), input);
            if(score != null) ctx.set(score, Dbl.get(0), input);
            iter = null;
            return true;
          } else if(!sub.next(ctx)) {
            // no more iterations from above, we're done here
            return false;
          }

          // next iteration, reset iterator and counter
          iter = expr.iter(ctx);
          p = 0;
        }
      }
    };
  }

  @Override
  public void plan(final Serializer ser) throws IOException {
    ser.openElement(this);
    if(empty) ser.attribute(Token.token(EMPTYORD), Token.TRUE);
    var.plan(ser);
    if(pos != null) {
      ser.openElement(Token.token(QueryText.AT));
      pos.plan(ser);
      ser.closeElement();
    }

    if(score != null) {
      ser.openElement(Token.token(QueryText.SCORE));
      score.plan(ser);
      ser.closeElement();
    }

    expr.plan(ser);
    ser.closeElement();
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder(FOR).append(' ').append(var);
    if(empty) sb.append(' ').append(ALLOWING).append(' ').append(EMPTYORD);
    if(pos != null) sb.append(' ').append(AT).append(' ').append(pos);
    if(score != null) sb.append(' ').append(SCORE).append(' ').append(score);
    return sb.append(' ').append(IN).append(' ').append(expr).toString();
  }

  @Override
  public boolean uses(final Use u) {
    return u == Use.VAR || expr.uses(u);
  }

  @Override
  public For comp(final QueryContext ctx, final VarScope scp) throws QueryException {
    expr = expr.comp(ctx, scp);
    return this;
  }

  @Override
  public boolean removable(final Var v) {
    return expr.removable(v);
  }

  @Override
  public Expr remove(final Var v) {
    expr = expr.remove(v);
    return this;
  }

  @Override
  public boolean visitVars(final VarVisitor visitor) {
    return expr.visitVars(visitor) && visitor.declared(var)
        && (pos == null || visitor.declared(pos))
        && (score == null || visitor.declared(score));
  }

  /**
   * Gathers all non-{@code null} variables.
   * @param v var
   * @param p pos
   * @param s scope
   * @return non-{@code null} variables
   */
  private static Var[] vars(final Var v, final Var p, final Var s) {
    return p == null ? s == null ? new Var[] { v } : new Var[] { v, s } :
      s == null ? new Var[] { v, p } : new Var[] { v, p, s };
  }
}
