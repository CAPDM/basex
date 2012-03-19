package org.basex.query.gflwor;

import java.io.*;
import java.util.*;

import org.basex.io.serial.*;
import org.basex.query.*;
import org.basex.query.expr.*;
import org.basex.query.gflwor.GFLWOR.Clause;
import org.basex.query.gflwor.GFLWOR.Eval;
import org.basex.query.item.*;
import org.basex.query.iter.*;
import org.basex.query.util.*;
import org.basex.query.var.*;
import org.basex.util.*;

import static org.basex.util.Token.token;
import static org.basex.query.QueryText.*;

/**
 * the GFLWOR {@code window} clause.
 *
 * @author BaseX Team 2005-12, BSD License
 * @author Leo Woerteler
 */
public class Window extends GFLWOR.Clause {
  /** {@code sliding window} flag. */
  final boolean sliding;
  /** The window variable. */
  final Var var;
  /** The sequence. */
  Expr expr;
  /** The start condition. */
  final Condition start;
  /** the {@code only} flag. */
  final boolean only;
  /** The end condition, possibly {@code null}. */
  final Condition end;

  /**
   * Constructor.
   * @param ii input info
   * @param slide {@code sliding window} flag
   * @param v window variable
   * @param in sequence
   * @param st start condition
   * @param o {@code only} flag
   * @param nd end condition
   * @throws QueryException query exception
   */
  public Window(final InputInfo ii, final boolean slide, final Var v, final Expr in,
      final Condition st, final boolean o, final Condition nd) throws QueryException {
    super(ii, vars(v, st, nd, ii));
    sliding = slide;
    var = v;
    expr = in;
    start = st;
    only = o;
    end = nd;
  }

  /**
   * Gathers all non-{@code null} variables.
   * @param vr window variable
   * @param st start condition
   * @param nd end condition, might be {@code null}
   * @param ii input info for the error message
   * @return non-{@code null} variables
   * @throws QueryException query exception if the variable names aren't unique
   */
  private static Var[] vars(final Var vr, final Condition st, final Condition nd,
      final InputInfo ii) throws QueryException {
    // determine the size of the array beforehand
    final int stn = st.nVars();
    final Var[] vs = new Var[1 + stn + (nd == null ? 0 : nd.nVars())];

    // write variables to the array
    st.writeVars(vs, 0);
    if(nd != null) nd.writeVars(vs, stn);
    vs[vs.length - 1] = vr;

    // check for duplicates
    for(int i = 0; i < vs.length; i++) {
      final Var v = vs[i];
      for(int j = i; --j >= 0;)
        if(v.name.eq(vs[j].name)) throw Err.WINDOWUNIQ.thrw(ii, vs[j]);
    }
    return vs;
  }

  @Override
  Eval eval(final Eval sub) {
    return sliding ? slidingEval(sub) : end == null ? tumblingEval(sub)
        : tumblingEndEval(sub);
  }

  /**
   * Evaluator for tumbling windows.
   * @param sub wrapped evaluator
   * @return evaluator for tumbling windows
   */
  private Eval tumblingEval(final Eval sub) {
    return new TumblingEval() {
      /** Values for the current start item. */
      private Item[] vals;
      /** Position of the start item. */
      private long spos;
      @Override
      public boolean next(final QueryContext ctx) throws QueryException {
        while(true) {
          // find first item
          final Item fst = vals != null ? vals[0] : findStart(ctx) ? curr : null;

          // find end item
          if(fst != null) {
            final ItemCache window = new ItemCache(new Item[] {fst, null, null, null}, 1);
            final Item[] st = vals == null ? new Item[] { curr, prev, next } : vals;
            final long ps = vals == null ? p : spos;
            vals = null;

            while(readNext()) {
              if(start.matches(ctx, curr, p, prev, next)) {
                vals = new Item[]{ curr, prev, next };
                spos = p;
                start.bind(ctx, st[0], ps, st[1], st[2]);
                break;
              }
              window.add(curr);
            }

            ctx.set(var, window.value(), input);
            return true;
          }

          // no more iterations from above, we're done here
          if(!prepareNext(ctx, sub)) return false;
          vals = null;
        }
      }
    };
  }

  /**
   * Evaluator for tumbling windows with an {@code end} condition.
   * @param sub wrapped evaluator
   * @return evaluator for tumbling windows
   */
  private Eval tumblingEndEval(final Eval sub) {
    return new TumblingEval() {
      @Override
      public boolean next(final QueryContext ctx) throws QueryException {
        while(true) {
          if(findStart(ctx)) {
            // find end item
            final ItemCache window = new ItemCache();
            boolean found = false;
            do {
              window.add(curr);
              if(end.matches(ctx, curr, p, prev, next)) {
                found = true;
                break;
              }
            } while(readNext());

            // don't return dangling items if the {@code only} flag was specified
            if(found || !only) {
              ctx.set(var, window.value(), input);
              return true;
            }
          }

          // no more iterations from above, we're done here
          if(!prepareNext(ctx, sub)) return false;
        }
      }
    };
  }

  /**
   * Evaluator for sliding windows.
   * @param sub wrapped evaluator
   * @return evaluator for tumbling windows
   */
  private Eval slidingEval(final Eval sub) {
    return new WindowEval() {
      /** Queue holding the items of the current window. */
      private final ArrayDeque<Item> queue = new ArrayDeque<Item>();
      @Override
      public boolean next(final QueryContext ctx) throws QueryException {
        while(true) {
          Item curr, next = null;
          while((curr = advance()) != null) {
            next = queue.peekFirst();
            if(next == null && (next = next()) != null) queue.addLast(next);
            if(start.matches(ctx, curr, p, prev, next)) break;
            prev = curr;
          }

          if(curr != null) {
            final ItemCache cache = new ItemCache();
            final Iterator<Item> qiter = queue.iterator();
            // the first element is already the {@code next} one
            if(qiter.hasNext()) qiter.next();
            Item pr = prev, it = curr, nx = next;
            long ps = p;
            do {
              cache.add(it);
              if(end.matches(ctx, it, ps++, pr, nx)) break;
              pr = it;
              it = nx;
              if(qiter.hasNext()) {
                nx = qiter.next();
              } else {
                nx = next();
                if(nx != null) queue.addLast(nx);
              }
            } while(it != null);

            // return window if end was found or {@code only} isn't set
            if(!(it == null && only)) {
              start.bind(ctx, curr, p, prev, next);
              prev = curr;
              ctx.set(var, cache.value(), input);
              return true;
            }
          }

          // abort if no more tuples from above
          if(!prepareNext(ctx, sub)) return false;
          queue.clear();
        }
      }

      /**
       * tries to advance the start of the queue by one element and returns the removed
       * element in case of success, {@code null} otherwise.
       * @return removed element or {@code null}
       * @throws QueryException evaluation exception
       */
      private Item advance() throws QueryException {
        Item it = queue.pollFirst();
        if(it == null) it = next();
        if(it != null) p++;
        return it;
      }
    };
  }

  @Override
  public Clause comp(final QueryContext ctx, final VarScope scp) throws QueryException {
    expr = expr.comp(ctx, scp);
    start.comp(ctx, scp);
    if(end != null) end.comp(ctx, scp);
    return this;
  }

  @Override
  public boolean uses(final Use u) {
    return u == Use.X30 || expr.uses(u) || start.uses(u) || end != null && end.uses(u);
  }

  @Override
  public boolean removable(final Var v) {
    return expr.removable(v) && start.removable(v) && (end == null || end.removable(v));
  }

  @Override
  public Expr remove(final Var v) {
    expr = expr.remove(v);
    start.remove(v);
    if(end != null) end.remove(v);
    return this;
  }

  @Override
  public boolean visitVars(final VarVisitor visitor) {
    return expr.visitVars(visitor) && start.visitVars(visitor)
        && (end == null || end.visitVars(visitor)) && visitor.declared(var);
  }

  @Override
  public void plan(final Serializer ser) throws IOException {
    ser.openElement(this, token(SLIDING), token(sliding));
    var.plan(ser);
    expr.plan(ser);
    start.plan(ser);
    if(end != null) end.plan(ser);
    ser.closeElement();
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder(FOR).append(' ').append(
        sliding ? SLIDING : TUMBLING).append(' ').append(WINDOW).append(' ').append(var
            ).append(' ').append(IN).append(' ').append(expr).append(' ').append(start);
    if(end != null) {
      if(only) sb.append(' ').append(ONLY);
      sb.append(' ').append(end);
    }
    return sb.toString();
  }

  /**
   * A window {@code start} of {@code end} condition.
   *
   * @author BaseX Team 2005-12, BSD License
   * @author Leo Woerteler
   */
  public static final class Condition extends Single {
    /** Start condition flag. */
    private final boolean start;
    /** Item variable. */
    private final Var item;
    /** Position variable. */
    private final Var pos;
    /** Previous item. */
    private final Var prev;
    /** Next item. */
    private final Var next;

    /**
     * Constructor.
     * @param st start condition flag
     * @param it item variable
     * @param p position variable
     * @param pr previous variable
     * @param nx next variable
     * @param cond condition expression
     * @param ii input info
     */
    public Condition(final boolean st, final Var it, final Var p, final Var pr,
        final Var nx, final Expr cond, final InputInfo ii) {
      super(ii, cond);
      start = st;
      item = it;
      pos = p;
      prev = pr;
      next = nx;
    }

    @Override
    public Expr comp(final QueryContext ctx, final VarScope scp) throws QueryException {
      expr = expr.comp(ctx, scp).compEbv(ctx);
      return this;
    }

    /**
     * Number of non-{@code null} variables in this condition.
     * @return number of variables
     */
    int nVars() {
      int i = 0;
      if(item != null) i++;
      if(pos  != null) i++;
      if(prev != null) i++;
      if(next != null) i++;
      return i;
    }

    /**
     * Checks if this condition binds the item following the current one in the input.
     * @return result of check
     */
    boolean usesNext() {
      return next != null;
    }

    /**
     * Write all non-{@code null} variables in this condition to the given array.
     * @param arr array to write to
     * @param p start position
     * @return the array for convenience
     */
    Var[] writeVars(final Var[] arr, final int p) {
      int i = p;
      if(item != null) arr[i++] = item;
      if(pos  != null) arr[i++] = pos;
      if(prev != null) arr[i++] = prev;
      if(next != null) arr[i++] = next;
      return arr;
    }

    /**
     * Binds the variables and checks if the item satisfies this condition.
     * @param ctx query context for variable binding
     * @param it current item
     * @param p position in the input sequence
     * @param pr previous item
     * @param nx next item
     * @return {@code true} if {@code it} matches the condition, {@code false} otherwise
     * @throws QueryException query exception
     */
    boolean matches(final QueryContext ctx, final Item it, final long p, final Item pr,
        final Item nx) throws QueryException {
      // bind variables
      bind(ctx, it, p, pr, nx);

      // evaluate as effective boolean value
      return expr.ebv(ctx, input).bool(input);
    }

    /**
     * Binds this condition's variables to the given values.
     * @param ctx query context
     * @param it current item
     * @param p position
     * @param pr previous item
     * @param nx next item
     * @throws QueryException query exception
     */
    void bind(final QueryContext ctx, final Item it, final long p, final Item pr,
        final Item nx) throws QueryException {
      if(item != null) ctx.set(item, it == null ? Empty.SEQ : it, input);
      if(pos  != null) ctx.set(pos,  Int.get(p),                  input);
      if(prev != null) ctx.set(prev, pr == null ? Empty.SEQ : pr, input);
      if(next != null) ctx.set(next, nx == null ? Empty.SEQ : nx, input);
    }

    @Override
    public String toString() {
      final StringBuilder sb = new StringBuilder(start ? START : END);
      if(item != null) sb.append(' ').append(item);
      if(pos  != null) sb.append(' ').append(AT).append(' ').append(pos);
      if(prev != null) sb.append(' ').append(PREVIOUS).append(' ').append(prev);
      if(next != null) sb.append(' ').append(NEXT).append(' ').append(next);
      return sb.append(' ').append(WHEN).append(' ').append(expr).toString();
    }

    @Override
    public void plan(final Serializer ser) throws IOException {
      ser.openElement(token(start ? START : END));

      // mapping variable names to roles
      if(item != null) ser.attribute(VAR, token(item.toString()));
      if(pos  != null) ser.attribute(token(AT), token(pos.toString()));
      if(prev != null) ser.attribute(token(PREVIOUS), token(prev.toString()));
      if(next != null) ser.attribute(token(NEXT), token(next.toString()));

      // IDs and stack slots
      if(item != null) item.plan(ser);
      if(pos  != null) pos.plan(ser);
      if(prev != null) prev.plan(ser);
      if(next != null) next.plan(ser);

      expr.plan(ser);
      ser.closeElement();
    }

    @Override
    public boolean visitVars(final VarVisitor visitor) {
      return (item == null || visitor.declared(item))
          && (pos  == null || visitor.declared(pos))
          && (prev == null || visitor.declared(prev))
          && (next == null || visitor.declared(next))
          && expr.visitVars(visitor);
    }
  }

  /**
   * Evaluator for the Window clause.
   *
   * @author BaseX Team 2005-12, BSD License
   * @author Leo Woerteler
   */
  abstract class WindowEval implements Eval {
    /** Expression iterator. */
    Iter iter;
    /** Previous item. */
    Item prev;
    /** Current position. */
    long p;


    /**
     * Reads the next item from {@code iter} if it isn't {@code null} and sets it to
     * {@code null} if it's drained.
     * @return success flag
     * @throws QueryException evaluation exception
     */
    final Item next() throws QueryException {
      if(iter == null) return null;
      final Item it = iter.next();
      if(it == null) iter = null;
      return it;
    }

    /**
     * Tries to prepare the next round.
     * @param ctx query context
     * @param sub sub-evaluator
     * @return {@code true} if the next round could be prepared, {@code false} otherwise
     * @throws QueryException evaluation exception
     */
    boolean prepareNext(final QueryContext ctx, final Eval sub) throws QueryException {
      if(!sub.next(ctx)) return false;
      iter = expr.iter(ctx);
      prev = null;
      p = 0;
      return true;
    }
  }

  /**
   * Evaluator for the Tumbling Window clause.
   *
   * @author BaseX Team 2005-12, BSD License
   * @author Leo Woerteler
   */
  abstract class TumblingEval extends WindowEval {
    /** Current item. */
    Item curr;
    /** Next item. */
    Item next;
    /** If the next item is used. */
    final boolean popNext = start.usesNext() || end != null && end.usesNext();

    /**
     * Reads a new current item and populates the {@code nxt} variable if it's used.
     * @return next item
     * @throws QueryException evaluation exception
     */
    final boolean readNext() throws QueryException {
      prev = curr;
      p++;
      final Item n = next();
      // serve the stored item if available
      if(next != null) {
        curr = next;
        next = n;
      } else if(n != null && popNext) {
        // only assign if necessary
        next = next();
        curr = n;
      } else {
        curr = n;
      }
      return curr != null;
    }

    /**
     * Finds the next item in the sequence satisfying the start condition.
     * @param ctx query context
     * @return {@code true} if the current binding satisfies the start condition,
     *   {@code false} otherwise
     * @throws QueryException evaluation exception
     */
    final boolean findStart(final QueryContext ctx) throws QueryException {
      while(readNext())
        if(start.matches(ctx, curr, p, prev, next)) return true;
      return false;
    }

    @Override
    boolean prepareNext(final QueryContext ctx, final Eval sub) throws QueryException {
      if(!super.prepareNext(ctx, sub)) return false;
      curr = null;
      next = null;
      return true;
    }
  }
}
