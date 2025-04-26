package com.googlecode.dex2jar.ir.ts;

import com.googlecode.dex2jar.ir.IrMethod;
import com.googlecode.dex2jar.ir.expr.Local;
import com.googlecode.dex2jar.ir.expr.RefExpr;
import com.googlecode.dex2jar.ir.expr.Value;
import com.googlecode.dex2jar.ir.expr.Value.VT;
import com.googlecode.dex2jar.ir.stmt.Stmt;
import com.googlecode.dex2jar.ir.stmt.Stmt.ST;
import com.googlecode.dex2jar.ir.ts.an.SimpleLiveAnalyze;
import com.googlecode.dex2jar.ir.ts.an.SimpleLiveValue;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Ir2JRegAssignTransformer implements Transformer {

    public static class Reg {

        public Set<Reg> excludes = new HashSet<Reg>(4);

        public Set<Reg> prefers = new HashSet<Reg>(3);

        int reg = -1;

        public char type;

    }

    private static final Comparator<Reg> ORDER_REG_ASSIGN_BY_PREFERRED_SIZE_DESC = new Comparator<Reg>() {
        public int compare(Reg o1, Reg o2) {
            int x = o2.prefers.size() - o1.prefers.size();
            if (x == 0) {
                x = o2.excludes.size() - o1.excludes.size();
            }
            return x;
        }
    };

    private Reg[] genGraph(IrMethod method, final Reg[] regs) {
        Reg[] args;
        if (method.isStatic) {
            args = new Reg[method.args.length];
        } else {
            args = new Reg[method.args.length + 1];
        }

        Set<Stmt> tos = new HashSet<Stmt>();
        for (Stmt stmt : method.stmts) {
            if (stmt.st == ST.ASSIGN || stmt.st == ST.IDENTITY) {
                if (stmt.getOp1().vt == VT.LOCAL) {
                    Local left = (Local) stmt.getOp1();
                    Value op2 = stmt.getOp2();
                    int idx = left.lsIndex;
                    Reg leftReg = regs[idx];

                    Cfg.collectTos(stmt, tos);
                    for (Stmt next : tos) {
                        SimpleLiveValue[] frame = (SimpleLiveValue[]) next.frame;
                        if (frame == null) {
                            continue;
                        }
                        for (int i = 0; i < frame.length; i++) {
                            if (i == idx) {
                                continue;
                            }
                            SimpleLiveValue v = frame[i];
                            if (v != null && v.used) {
                                Reg rightReg = regs[i];
                                leftReg.excludes.add(rightReg);
                                rightReg.excludes.add(leftReg);
                            }
                        }
                    }
                    tos.clear();

                    if (op2.vt == VT.LOCAL) {
                        Reg rightReg = regs[((Local) op2).lsIndex];
                        leftReg.prefers.add(rightReg);
                        rightReg.prefers.add(leftReg);
                    }

                    if (op2.vt == VT.THIS_REF) {
                        args[0] = leftReg;
                    } else if (op2.vt == VT.PARAMETER_REF) {
                        RefExpr refExpr = (RefExpr) op2;
                        if (method.isStatic) {
                            args[refExpr.parameterIndex] = leftReg;
                        } else {
                            args[refExpr.parameterIndex + 1] = leftReg;
                        }
                    }
                }
            }
        }
        for (Reg reg : regs) {
            reg.excludes.remove(reg);
            reg.prefers.remove(reg);
        }
        return args;
    }

    Map<Character, List<Reg>> groupAndCleanUpByType(Reg[] regs) {
        Map<Character, List<Reg>> groups = new HashMap<Character, List<Reg>>();
        for (Reg reg : regs) {
            char simpleType = reg.type;
            List<Reg> group = groups.get(simpleType);
            if (group == null) {
                group = new ArrayList<Reg>();
                groups.put(simpleType, group);
            }
            group.add(reg);

            for (Reg ex : new HashSet<Reg>(reg.excludes)) {
                if (ex.type != reg.type) {
                    reg.excludes.remove(ex);
                }
            }
            for (Reg ex : new HashSet<Reg>(reg.prefers)) {
                if (ex.type != reg.type) {
                    reg.prefers.remove(ex);
                }
            }
        }
        return groups;
    }

    private void initExcludeColor(BitSet excludeColor, Reg as) {
        excludeColor.clear();
        for (Reg ex : as.excludes) {
            if (ex.reg >= 0) {
                excludeColor.set(ex.reg);
                if (ex.type == 'J' || ex.type == 'D') {
                    excludeColor.set(ex.reg + 1);
                }
            }
        }
    }

    private void initSuggestColor(BitSet suggestColor, Reg as) {
        suggestColor.clear();
        for (Reg ex : as.prefers) {
            if (ex.reg >= 0) {
                suggestColor.set(ex.reg);
            }
        }
    }

    @Override
    public void transform(IrMethod method) {
        if (method.locals.isEmpty()) {
            return;
        }
        SimpleLiveAnalyze sa = new SimpleLiveAnalyze(method, true);
        sa.analyze();

        int maxLocalSize = sa.getLocalSize();
        final Reg[] regs = new Reg[maxLocalSize];
        for (Local local : method.locals) {
            Reg reg = new Reg();
            char type = local.valueType.charAt(0);
            if (type == '[') {
                type = 'L';
            }
            reg.type = type;
            local.tag = reg;
            regs[local.lsIndex] = reg;
        }

        Reg[] args = genGraph(method, regs);

        if (!method.isStatic) {
            Reg atThis = args[0];
            for (Reg reg : regs) {
                if (reg == atThis) {
                    continue;
                }
                reg.excludes.add(atThis);
                atThis.excludes.add(reg);
            }
        }

        int i = 0;
        int index = 0;
        if (!method.isStatic) {
            args[i++].reg = index++;
        }
        for (int j = 0; j < method.args.length; j++) {
            Reg reg = args[i++];
            String type = method.args[j];
            if (reg == null) {
                index++;
            } else {
                reg.reg = index++;
            }
            if ("J".equals(type) || "D".equals(type)) {
                index++;
            }
        }

        Map<Character, List<Reg>> groups = groupAndCleanUpByType(regs);

        BitSet excludeColor = new BitSet();
        BitSet suggestColor = new BitSet();
        BitSet globalExcludes = new BitSet();
        BitSet usedInOneType = new BitSet();
        for (Map.Entry<Character, List<Reg>> e : groups.entrySet()) {
            List<Reg> assigns = e.getValue();
            assigns.sort(ORDER_REG_ASSIGN_BY_PREFERRED_SIZE_DESC);
            char type = e.getKey();
            boolean doubleOrLong = type == 'J' || type == 'D';
            for (Reg as : assigns) {
                if (as.reg < 0) {
                    initExcludeColor(excludeColor, as);
                    excludeParameters(excludeColor, args, type);

                    excludeColor.or(globalExcludes);

                    initSuggestColor(suggestColor, as);

                    for (int j = suggestColor.nextSetBit(0); j >= 0; j = suggestColor.nextSetBit(j + 1)) {
                        if (doubleOrLong) {
                            if (!excludeColor.get(j) && !excludeColor.get(j + 1)) {
                                as.reg = j;
                                break;
                            }
                        } else {
                            if (!excludeColor.get(j)) {
                                as.reg = j;
                                break;
                            }
                        }
                    }
                    if (as.reg < 0) {
                        if (doubleOrLong) {
                            int reg = -1;
                            do {
                                reg++;
                                reg = excludeColor.nextClearBit(reg);
                            } while (excludeColor.get(reg + 1));
                            as.reg = reg;
                        } else {
                            as.reg = excludeColor.nextClearBit(0);
                        }
                    }
                }
                usedInOneType.set(as.reg);
                if (doubleOrLong) {
                    usedInOneType.set(as.reg + 1);
                }
            }
            globalExcludes.or(usedInOneType);
            usedInOneType.clear();
        }

        for (Local local : method.locals) {
            Reg as = (Reg) local.tag;
            local.lsIndex = as.reg;
            local.tag = null;
        }
        for (Stmt stmt : method.stmts) {
            stmt.frame = null;
        }
    }

    private void excludeParameters(BitSet excludeColor, Reg[] args, char type) {
        for (Reg arg : args) {
            if (arg.type != type) {
                excludeColor.set(arg.reg);
                if (arg.type == 'J' || arg.type == 'D') {
                    excludeColor.set(arg.reg + 1);
                }
            }
        }
    }
}

