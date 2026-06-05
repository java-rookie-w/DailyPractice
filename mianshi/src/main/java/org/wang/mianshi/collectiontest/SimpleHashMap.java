package org.wang.mianshi.collectiontest;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;

/**
 * A simplified {@code HashMap} that mirrors the real JDK 8 {@code java.util.HashMap}
 * internals closely enough to serve as <b>senior-Java-developer interview
 * preparation</b>.
 *
 * <h3>Concepts demonstrated</h3>
 * <ol>
 *   <li><b>tableSizeFor</b> — round capacity to next power of two (required for
 *       bitwise indexing).</li>
 *   <li><b>hash()</b> — the "disturbance function" that XORs high bits into low bits
 *       to reduce collisions in small tables.</li>
 *   <li><b>(n-1) &amp; hash</b> — bitwise bucket-index instead of {@code hash % n}
 *       (only works when n is a power of two).</li>
 *   <li><b>Separate-chaining</b> — linked-list collision resolution.</li>
 *   <li><b>Load-factor resize</b> — when {@code size > capacity × loadFactor} the
 *       table doubles.</li>
 *   <li><b>JDK 8 lo/hi split</b> — during resize, a single bucket is split into two
 *       sub-chains using {@code hash & oldCap}, avoiding per-node re-computation.
 *       <i>This eliminates the JDK 7 infinite-loop bug under concurrent
 *       modification.</i></li>
 *   <li><b>Treeification</b> — when a bucket chain reaches {@link #TREEIFY_THRESHOLD}
 *       (8), it is converted to a <b>red-black tree</b> so that worst-case lookup
 *       degrades to O(log n) instead of O(n).  If the table is still too small
 *       (&lt; {@link #MIN_TREEIFY_CAPACITY}) the map resizes instead.</li>
 *   <li><b>Red-black tree operations</b> — rotations, balance-after-insertion,
 *       balance-after-deletion, tree-split during resize, untreeify.</li>
 * </ol>
 *
 * <h3>Why TREEIFY_THRESHOLD = 8</h3>
 * Under a well-distributed hash function the number of entries per bucket follows a
 * Poisson distribution with λ ≈ 0.5.  P(bucket-length ≥ 8) ≈ 0.00000006 — so
 * treeification is virtually never needed by accident; when it <i>does</i> happen it
 * almost certainly indicates a deliberate hash-collision attack (DoS), which the tree
 * mitigates by keeping operations O(log n).
 *
 * <h3>Thread-safety note</h3>
 * This implementation is <b>not</b> thread-safe, just like {@code java.util.HashMap}.
 * JDK 7's concurrent resize could produce an infinite loop (circular linked list).
 * JDK 8's lo/hi split fixes that specific bug, but concurrent puts can still cause
 * data loss or NPE.  Use {@code ConcurrentHashMap} when you need concurrency.
 *
 * @param <K> key type
 * @param <V> value type
 */
@SuppressWarnings("unchecked")
public class SimpleHashMap<K, V> {

    // ---- Constants (exact copies of java.util.HashMap) ---------------------------

    static final int DEFAULT_INITIAL_CAPACITY = 1 << 4; // 16
    static final int MAXIMUM_CAPACITY = 1 << 30;
    static final float DEFAULT_LOAD_FACTOR = 0.75f;

    /** Chain-length threshold that triggers treeification. */
    static final int TREEIFY_THRESHOLD = 8;

    /** Chain-length threshold that triggers de-treeification. */
    static final int UNTREEIFY_THRESHOLD = 6;

    /** Minimum table capacity before treeification is even considered. */
    static final int MIN_TREEIFY_CAPACITY = 64;

    // ---- Fields ---------------------------------------------------------------

    Node<K, V>[] table;
    int size;
    int threshold;

    // ---- Construction ---------------------------------------------------------

    public SimpleHashMap() {
        this.threshold = tableSizeFor(DEFAULT_INITIAL_CAPACITY);
    }

    // =========================================================================
    //  SECTION A — Core hash & index algorithms
    // =========================================================================

    static int tableSizeFor(int cap) {
        int n = cap - 1;
        n |= n >>> 1;
        n |= n >>> 2;
        n |= n >>> 4;
        n |= n >>> 8;
        n |= n >>> 16;
        return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
    }

    static int hash(Object key) {
        int h;
        return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
    }

    int index(int hash, int n) {
        return (n - 1) & hash;
    }

    // =========================================================================
    //  SECTION B — Node & TreeNode
    // =========================================================================

    static class Node<K, V> {
        final int hash;
        final K key;
        V value;
        Node<K, V> next;

        Node(int hash, K key, V value, Node<K, V> next) {
            this.hash = hash;
            this.key = key;
            this.value = value;
            this.next = next;
        }
    }

    /**
     * Red-black tree node — extends {@link Node} so the bucket can hold either a
     * plain linked list or a tree without changing the table type.
     */
    static final class TreeNode<K, V> extends Node<K, V> {
        TreeNode<K, V> parent;
        TreeNode<K, V> left;
        TreeNode<K, V> right;
        TreeNode<K, V> prev;
        boolean red;

        TreeNode(int hash, K key, V value, Node<K, V> next) {
            super(hash, key, value, next);
        }

        // ---- basic helpers ----

        TreeNode<K, V> root() {
            TreeNode<K, V> r = this;
            while (r.parent != null) r = r.parent;
            return r;
        }

        static <K, V> void moveRootToFront(Node<K, V>[] tab, TreeNode<K, V> root) {
            int n;
            if (root == null || tab == null || (n = tab.length) == 0) return;
            int i = (n - 1) & root.hash;
            TreeNode<K, V> first = (TreeNode<K, V>) tab[i];
            if (root != first) {
                tab[i] = root;
                TreeNode<K, V> rp = root.prev;
                if (root.next != null) ((TreeNode<K, V>) root.next).prev = rp;
                if (rp != null) rp.next = root.next;
                root.next = first;
                if (first != null) first.prev = root;
                root.prev = null;
            }
        }

        // ---- treeify / untreeify ----

        /**
         * Build a red-black tree from the linked list of TreeNodes starting at {@code this}.
         */
        void treeify(Node<K, V>[] tab) {
            TreeNode<K, V> root = null;
            for (TreeNode<K, V> x = this; x != null; x = (TreeNode<K, V>) x.next) {
                x.left = x.right = x.parent = null;
                x.red = true;

                if (root == null) {
                    root = x;
                } else {
                    K k = x.key;
                    int h = x.hash;
                    Class<?> kc = null;
                    TreeNode<K, V> p = root;
                    for (;;) {
                        int dir, ph = p.hash;
                        if ((dir = Integer.compare(h, ph)) == 0) {
                            if (kc == null && (kc = comparableClassFor(k)) != null)
                                dir = compareComparables(kc, k, p.key);
                            if (dir == 0)
                                dir = tieBreakOrder(k, p.key);
                        }
                        TreeNode<K, V> xp = p;
                        p = (dir <= 0) ? p.left : p.right;
                        if (p == null) {
                            x.parent = xp;
                            if (dir <= 0) xp.left = x; else xp.right = x;
                            root = balanceInsertion(root, x);
                            break;
                        }
                    }
                }
            }
            moveRootToFront(tab, root);
        }

        Node<K, V> untreeify(SimpleHashMap<K, V> map) {
            Node<K, V> hd = null, tl = null;
            for (Node<K, V> q = this; q != null; q = q.next) {
                Node<K, V> p = map.replacementNode(q.hash, q.key, q.value, null);
                if (tl == null) hd = p; else tl.next = p;
                tl = p;
            }
            return hd;
        }

        // ---- get / put in tree ----

        TreeNode<K, V> getTreeNode(int h, Object k) {
            return ((parent != null) ? root() : this).find(h, k);
        }

        /**
         * Search from {@code this} downward (does NOT travel to root).
         * Uses the JDK 8 loop-based approach — when hashes are equal and
         * keys are neither equal nor Comparable-ordered, it recursively
         * searches the right subtree, then falls through to the left.
         */
        TreeNode<K, V> find(int h, Object k) {
            TreeNode<K, V> p = this;
            Class<?> kc = null;
            do {
                int ph = p.hash;
                TreeNode<K, V> pl = p.left, pr = p.right;
                if (ph > h) {
                    p = pl;
                } else if (ph < h) {
                    p = pr;
                } else if (Objects.equals(p.key, k)) {
                    return p;
                } else if (pl == null) {
                    p = pr;
                } else if (pr == null) {
                    p = pl;
                } else if ((kc != null || (kc = comparableClassFor(k)) != null)
                        && (Integer.compare(
                            compareComparables(kc, k, p.key), 0)) != 0) {
                    int dir = compareComparables(kc, k, p.key);
                    p = (dir < 0) ? pl : pr;
                } else {
                    TreeNode<K, V> q = pr.find(h, k);
                    if (q != null) return q;
                    p = pl;
                }
            } while (p != null);
            return null;
        }

        TreeNode<K, V> putTreeVal(SimpleHashMap<K, V> map, Node<K, V>[] tab,
                                  int h, K k, V v) {
            TreeNode<K, V> root = root();
            Class<?> kc = null;
            boolean searched = false;

            for (TreeNode<K, V> p = root;;) {
                int dir, ph = p.hash;
                if ((dir = Integer.compare(h, ph)) == 0) {
                    if (Objects.equals(p.key, k)) return p;
                    if (kc == null && (kc = comparableClassFor(k)) != null)
                        dir = compareComparables(kc, k, p.key);
                    if (dir == 0 && !searched) {
                        TreeNode<K, V> found;
                        if (p.left  != null && (found = p.left.getTreeNode(h, k))  != null) return found;
                        if (p.right != null && (found = p.right.getTreeNode(h, k)) != null) return found;
                        searched = true;
                        dir = tieBreakOrder(k, p.key);
                    }
                }

                TreeNode<K, V> xp = p;
                p = (dir <= 0) ? p.left : p.right;
                if (p == null) {
                    Node<K, V> xpn = xp.next;
                    TreeNode<K, V> x = new TreeNode<>(h, k, v, xpn);
                    if (dir <= 0) xp.left = x; else xp.right = x;
                    xp.next = x;
                    x.parent = x.prev = xp;
                    if (xpn != null) ((TreeNode<K, V>) xpn).prev = x;
                    moveRootToFront(tab, balanceInsertion(root, x));
                    return null;
                }
            }
        }

        // ---- remove from tree ----

        void removeTreeNode(SimpleHashMap<K, V> map, Node<K, V>[] tab, boolean movable) {
            int n;
            if (tab == null || (n = tab.length) == 0) return;

            int i = (n - 1) & hash;
            TreeNode<K, V> first = (TreeNode<K, V>) tab[i], root = first.root();
            if (root == null) return;

            // Unlink from the prev/next chain
            if (prev != null) prev.next = next;
            if (next != null) ((TreeNode<K, V>) next).prev = prev;
            if (first == this) tab[i] = first = (TreeNode<K, V>) next;
            if (first == null) return;

            // Small tree → fall back to linked list
            if (root.parent != null) root = root.root();
            if (root == null || (movable && (root.right == null
                    || root.left == null || root.left.left == null))) {
                tab[i] = first.untreeify(map);
                return;
            }

            TreeNode<K, V> p = this, pl = left, pr = right, replacement;
            if (pl != null && pr != null) {
                // Two children: swap with successor
                TreeNode<K, V> s = pr, sl;
                while ((sl = s.left) != null) s = sl;
                boolean c = s.red; s.red = p.red; p.red = c;
                TreeNode<K, V> sp = s.parent, sr = s.right;

                if (s == pr) {
                    p.parent = s;
                    s.right = p;
                } else {
                    p.parent = sp;
                    if (sp != null) { if (sp.left == s) sp.left = p; else sp.right = p; }
                    s.right = pr;
                    if (pr != null) pr.parent = s;
                }
                p.left = null;
                s.left = pl;
                if (pl != null) pl.parent = s;
                p.right = sr;
                if (sr != null) sr.parent = p;

                if (p == root) root = s;
                else if (root.left == s) root.left = p;
                else if (root.right == s) root.right = p;

                pr = p.right; pl = null;
                if (pr != null) pr.parent = p;
                if (p == root) root = p;
            }

            replacement = (pl != null) ? pl : pr;

            if (replacement != null) {
                replacement.parent = p.parent;
                if (p.parent == null) root = replacement;
                else if (p == p.parent.left) p.parent.left = replacement;
                else p.parent.right = replacement;
                p.left = p.right = p.parent = null;
                if (!p.red) root = balanceDeletion(root, replacement);
            } else if (p.parent == null) {
                root = null;
            } else {
                if (!p.red) root = balanceDeletion(root, p);
                if (p.parent != null) {
                    if (p == p.parent.left) p.parent.left = null;
                    else p.parent.right = null;
                    p.parent = null;
                }
            }

            if (movable) moveRootToFront(tab, root);
        }

        // ---- split during resize ----

        void split(SimpleHashMap<K, V> map, Node<K, V>[] tab, int index, int bit) {
            TreeNode<K, V> b = this;
            TreeNode<K, V> loHead = null, loTail = null;
            TreeNode<K, V> hiHead = null, hiTail = null;
            int lc = 0, hc = 0;

            for (TreeNode<K, V> e = b; e != null; e = (TreeNode<K, V>) e.next) {
                TreeNode<K, V> next = (TreeNode<K, V>) e.next;
                e.next = null;
                if ((e.hash & bit) == 0) {
                    e.prev = loTail;
                    if (loTail == null) loHead = e; else loTail.next = e;
                    loTail = e; lc++;
                } else {
                    e.prev = hiTail;
                    if (hiTail == null) hiHead = e; else hiTail.next = e;
                    hiTail = e; hc++;
                }
            }

            if (loHead != null) {
                if (lc <= UNTREEIFY_THRESHOLD) tab[index] = loHead.untreeify(map);
                else { tab[index] = loHead; if (hiHead != null) loHead.treeify(tab); }
            }
            if (hiHead != null) {
                if (hc <= UNTREEIFY_THRESHOLD) tab[index + bit] = hiHead.untreeify(map);
                else { tab[index + bit] = hiHead; if (loHead != null) hiHead.treeify(tab); }
            }
        }

        // ---- RB-tree rotations & balancing (static) ------------------------

        static <K, V> TreeNode<K, V> rotateLeft(TreeNode<K, V> root, TreeNode<K, V> p) {
            TreeNode<K, V> r = p.right;
            if (r == null) return root;
            if ((p.right = r.left) != null) r.left.parent = p;
            if ((r.parent = p.parent) == null) root = r;
            else if (p.parent.left == p) p.parent.left = r;
            else p.parent.right = r;
            r.left = p;
            p.parent = r;
            return root;
        }

        static <K, V> TreeNode<K, V> rotateRight(TreeNode<K, V> root, TreeNode<K, V> p) {
            TreeNode<K, V> l = p.left;
            if (l == null) return root;
            if ((p.left = l.right) != null) l.right.parent = p;
            if ((l.parent = p.parent) == null) root = l;
            else if (p.parent.right == p) p.parent.right = l;
            else p.parent.left = l;
            l.right = p;
            p.parent = l;
            return root;
        }

        /**
         * Fix red-black invariants after inserting node {@code x}.
         *
         * <pre>
         * Loop while x's parent is RED:
         *   Case 1: uncle is RED → recolor parent & uncle BLACK, grandparent RED, x = grandparent
         *   Case 2: uncle is BLACK, x is right child of left child → rotate left on parent
         *   Case 3: uncle is BLACK, x is left child of left child → rotate right on grandparent
         *   (Mirror for right-side cases.)
         * </pre>
         */
        static <K, V> TreeNode<K, V> balanceInsertion(TreeNode<K, V> root, TreeNode<K, V> x) {
            x.red = true;
            for (TreeNode<K, V> xp, xpp, xppl, xppr;;) {
                if ((xp = x.parent) == null) { x.red = false; return x; }
                if (!xp.red || (xpp = xp.parent) == null) return root;

                if (xp == (xppl = xpp.left)) {
                    if ((xppr = xpp.right) != null && xppr.red) {
                        xppr.red = false; xp.red = false; xpp.red = true; x = xpp;
                    } else {
                        if (x == xp.right) { root = rotateLeft(root, x = xp); xpp = (xp = x.parent) == null ? null : xp.parent; }
                        if (xp != null) { xp.red = false; if (xpp != null) { xpp.red = true; root = rotateRight(root, xpp); } }
                    }
                } else {
                    if (xpp.left != null && xpp.left.red) {
                        xpp.left.red = false; xp.red = false; xpp.red = true; x = xpp;
                    } else {
                        if (x == xp.left) { root = rotateRight(root, x = xp); xpp = (xp = x.parent) == null ? null : xp.parent; }
                        if (xp != null) { xp.red = false; if (xpp != null) { xpp.red = true; root = rotateLeft(root, xpp); } }
                    }
                }
            }
        }

        /**
         * Fix red-black invariants after deleting a node.
         */
        static <K, V> TreeNode<K, V> balanceDeletion(TreeNode<K, V> root, TreeNode<K, V> x) {
            for (TreeNode<K, V> xp, sib;;) {
                if (x == null || x == root) return root;
                if ((xp = x.parent) == null) { x.red = false; return x; }
                if (x.red) { x.red = false; return root; }

                boolean leftOfParent = (x == xp.left);
                sib = leftOfParent ? xp.right : xp.left;

                if (leftOfParent) {
                    if (sib.red) {
                        sib.red = false; xp.red = true;
                        root = rotateLeft(root, xp); sib = (xp = x.parent) == null ? null : xp.right;
                    }
                    if (sib == null) { x = xp; }
                    else if ((sib.right == null || !sib.right.red) && (sib.left == null || !sib.left.red)) {
                        sib.red = true; x = xp;
                    } else {
                        if (sib.right == null || !sib.right.red) {
                            if (sib.left != null) sib.left.red = false;
                            sib.red = true; root = rotateRight(root, sib);
                            sib = (xp = x.parent) == null ? null : xp.right;
                        }
                        if (sib != null) { sib.red = xp.red; if (sib.right != null) sib.right.red = false; }
                        xp.red = false; root = rotateLeft(root, xp); x = root;
                    }
                } else {
                    if (sib.red) {
                        sib.red = false; xp.red = true;
                        root = rotateRight(root, xp); sib = (xp = x.parent) == null ? null : xp.left;
                    }
                    if (sib == null) { x = xp; }
                    else if ((sib.left == null || !sib.left.red) && (sib.right == null || !sib.right.red)) {
                        sib.red = true; x = xp;
                    } else {
                        if (sib.left == null || !sib.left.red) {
                            if (sib.right != null) sib.right.red = false;
                            sib.red = true; root = rotateLeft(root, sib);
                            sib = (xp = x.parent) == null ? null : xp.left;
                        }
                        if (sib != null) { sib.red = xp.red; if (sib.left != null) sib.left.red = false; }
                        xp.red = false; root = rotateRight(root, xp); x = root;
                    }
                }
            }
        }
    } // end of TreeNode

    // =========================================================================
    //  SECTION C — Public API
    // =========================================================================

    public V put(K key, V value) {
        if (table == null || table.length == 0) {
            table = (Node<K, V>[]) new Node[tableSizeFor(threshold)];
            threshold = (int) (table.length * DEFAULT_LOAD_FACTOR);
        }

        int hash = hash(key);
        int i = index(hash, table.length);
        Node<K, V> p = table[i];

        if (p == null) {
            table[i] = replacementNode(hash, key, value, null);
        } else {
            Node<K, V> e;
            if (p.hash == hash && Objects.equals(p.key, key)) {
                e = p;
            } else if (p instanceof TreeNode) {
                e = ((TreeNode<K, V>) p).putTreeVal(this, table, hash, key, value);
            } else {
                for (int binCount = 0; ; binCount++) {
                    e = p.next;
                    if (e == null) {
                        p.next = replacementNode(hash, key, value, null);
                        if (binCount >= TREEIFY_THRESHOLD - 1)
                            treeifyBin(table, hash);
                        break;
                    }
                    if (e.hash == hash && Objects.equals(e.key, key)) break;
                    p = e;
                }
            }

            if (e != null) { V old = e.value; e.value = value; return old; }
        }

        size++;
        if (size > threshold) resize();
        return null;
    }

    // Package-private factory used by TreeNode.untreeify as well.
    Node<K, V> replacementNode(int hash, K key, V value, Node<K, V> next) {
        return new Node<>(hash, key, value, next);
    }

    public V get(Object key) {
        Node<K, V> e = getNode(hash(key), key);
        return e == null ? null : e.value;
    }

    Node<K, V> getNode(int hash, Object key) {
        if (table == null || table.length == 0) return null;

        int i = index(hash, table.length);
        Node<K, V> first = table[i];
        if (first == null) return null;

        if (first.hash == hash && Objects.equals(first.key, key)) return first;

        Node<K, V> e = first.next;
        if (e == null) return null;

        if (first instanceof TreeNode)
            return ((TreeNode<K, V>) first).getTreeNode(hash, key);

        do { if (e.hash == hash && Objects.equals(e.key, key)) return e;
        } while ((e = e.next) != null);
        return null;
    }

    public V remove(Object key) {
        Node<K, V> e = removeNode(hash(key), key, null, false);
        return e == null ? null : e.value;
    }

    Node<K, V> removeNode(int hash, Object key, Object matchValue, boolean matchValueFlag) {
        if (table == null || table.length == 0) return null;

        int i = index(hash, table.length);
        Node<K, V> p = table[i];
        if (p == null) return null;

        Node<K, V> node = null, e;
        if (p.hash == hash && Objects.equals(p.key, key)) {
            node = p;
        } else if ((e = p.next) != null) {
            if (p instanceof TreeNode)
                node = ((TreeNode<K, V>) p).getTreeNode(hash, key);
            else do { if (e.hash == hash && Objects.equals(e.key, key)) { node = e; break; }
                p = e;
            } while ((e = e.next) != null);
        }

        if (node != null && (!matchValueFlag || Objects.equals(node.value, matchValue))) {
            if (node instanceof TreeNode)
                ((TreeNode<K, V>) node).removeTreeNode(this, table, true);
            else if (node == p) table[i] = node.next;
            else p.next = node.next;
            size--;
            return node;
        }
        return null;
    }

    // =========================================================================
    //  SECTION D — Resize (JDK 8 lo/hi split)
    // =========================================================================

    /**
     * Doubles the table and redistributes entries using the lo/hi split.
     *
     * {@code hash & oldCap} extracts the one extra bit that determines whether
     * an entry stays at its current index or moves to {@code current + oldCap}.
     */
    void resize() {
        Node<K, V>[] oldTab = table;
        int oldCap = oldTab.length;

        if (oldCap >= MAXIMUM_CAPACITY) { threshold = Integer.MAX_VALUE; return; }

        int newCap = oldCap << 1;
        Node<K, V>[] newTab = (Node<K, V>[]) new Node[newCap];
        threshold = (int) (newCap * DEFAULT_LOAD_FACTOR);

        for (int j = 0; j < oldCap; j++) {
            Node<K, V> e = oldTab[j];
            if (e == null) continue;

            if (e.next == null) {
                newTab[e.hash & (newCap - 1)] = e;
            } else if (e instanceof TreeNode) {
                ((TreeNode<K, V>) e).split(this, newTab, j, oldCap);
            } else {
                Node<K, V> loHead = null, loTail = null;
                Node<K, V> hiHead = null, hiTail = null;
                while (e != null) {
                    Node<K, V> next = e.next;
                    if ((e.hash & oldCap) == 0) {
                        if (loTail == null) loHead = e; else loTail.next = e;
                        loTail = e;
                    } else {
                        if (hiTail == null) hiHead = e; else hiTail.next = e;
                        hiTail = e;
                    }
                    e = next;
                }
                if (loTail != null) { loTail.next = null; newTab[j] = loHead; }
                if (hiTail != null) { hiTail.next = null; newTab[j + oldCap] = hiHead; }
            }
        }
        table = newTab;
    }

    // =========================================================================
    //  SECTION E — Treeification entry point
    // =========================================================================

    void treeifyBin(Node<K, V>[] tab, int hash) {
        int n;
        if (tab == null || (n = tab.length) < MIN_TREEIFY_CAPACITY) {
            resize();
        } else {
            int i = (n - 1) & hash;
            if (tab[i] != null) {
                TreeNode<K, V> hd = null, tl = null;
                for (Node<K, V> e = tab[i]; e != null; e = e.next) {
                    TreeNode<K, V> p = new TreeNode<>(e.hash, e.key, e.value, null);
                    p.prev = tl;
                    if (tl == null) hd = p; else tl.next = p;
                    tl = p;
                }
                tab[i] = hd;
                if (hd != null) hd.treeify(tab);
            }
        }
    }

    // =========================================================================
    //  SECTION F — Comparable helpers (static)
    // =========================================================================

    static Class<?> comparableClassFor(Object x) {
        if (x instanceof Comparable) {
            Class<?> c = x.getClass();
            for (Type t : c.getGenericInterfaces()) {
                if (t instanceof ParameterizedType) {
                    ParameterizedType pt = (ParameterizedType) t;
                    if (pt.getRawType() == Comparable.class) {
                        Type[] args = pt.getActualTypeArguments();
                        if (args != null && args.length == 1 && args[0] == c) return c;
                    }
                }
            }
        }
        return null;
    }

    static int compareComparables(Class<?> kc, Object k, Object x) {
        return (x == null || x.getClass() != kc) ? 0 : ((Comparable<Object>) k).compareTo(x);
    }

    static int tieBreakOrder(Object a, Object b) {
        int d = (System.identityHashCode(a) <= System.identityHashCode(b) ? -1 : 1);
        return d != 0 ? d : -1;
    }

    // =========================================================================
    //  SECTION G — Utility & demo
    // =========================================================================

    public int size() { return size; }

    public void printInternals() {
        if (table == null) { System.out.println("table=null"); return; }
        System.out.println("capacity=" + table.length + "  size=" + size
                + "  threshold=" + threshold + "  loadFactor=" + DEFAULT_LOAD_FACTOR);
        for (int b = 0; b < table.length; b++) {
            Node<K, V> e = table[b];
            if (e == null) continue;
            int len = 0;
            boolean isTree = e instanceof TreeNode;
            for (Node<K, V> cur = e; cur != null; cur = cur.next) len++;
            System.out.printf("  bucket[%d] %s  length=%d  first-key=%s  hash=%s  index=%d%n",
                    b, isTree ? "TREE" : "LIST", len, e.key,
                    Integer.toHexString(e.hash), e.hash & (table.length - 1));
        }
    }

    @Override
    public String toString() {
        if (table == null) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Node<K, V>[] tab = table; ;) {
            for (int b = 0; b < tab.length; b++)
                for (Node<K, V> e = tab[b]; e != null; e = e.next) {
                    if (!first) sb.append(", ");
                    sb.append(e.key).append("=").append(e.value);
                    first = false;
                }
            break;
        }
        sb.append("}");
        return sb.toString();
    }

    // ---- Demo ------------------------------------------------------------------

    public static void main(String[] args) {
        System.out.println("========== 1. tableSizeFor (power-of-two rounding) ==========");
        System.out.println("tableSizeFor( 5) = " + tableSizeFor(5));    // 8
        System.out.println("tableSizeFor(10) = " + tableSizeFor(10));   // 16
        System.out.println("tableSizeFor(16) = " + tableSizeFor(16));   // 16
        System.out.println("tableSizeFor(17) = " + tableSizeFor(17));   // 32

        System.out.println("\n========== 2. hash() disturbance function ==========");
        String key = "HashMap";
        int hc = key.hashCode();
        int h = hash(key);
        System.out.printf("key='%s'%n", key);
        System.out.printf("  hashCode()       = %s (hex: %s)%n", hc, Integer.toHexString(hc));
        System.out.printf("  hash()           = %s (hex: %s)%n", h, Integer.toHexString(h));
        System.out.printf("  index in 16-slot = %d  (hash & 15 = %d)%n", h & 15, h & 15);

        System.out.println("\n========== 3. Basic put / get / remove ==========");
        SimpleHashMap<String, Integer> map = new SimpleHashMap<>();
        map.put("one", 1);
        map.put("two", 2);
        map.put("three", 3);
        System.out.println("map = " + map);
        System.out.println("get('two') = " + map.get("two"));
        System.out.println("remove('one') = " + map.remove("one"));
        System.out.println("get('one') after remove = " + map.get("one"));
        System.out.println("size = " + map.size());

        System.out.println("\n========== 4. Collision & treeification demo ==========");
        SimpleHashMap<BadHashKey, String> treeMap = new SimpleHashMap<>();
        for (int i = 0; i < 20; i++)
            treeMap.put(new BadHashKey(i), "val" + i);
        treeMap.printInternals();
        System.out.println("(table cap < 64 so treeifyBin calls resize instead; "
                + "with cap >= 64 the bucket would show TREE)");

        System.out.println("\n========== 5. Resize with lo/hi split ==========");
        SimpleHashMap<String, Integer> resizeMap = new SimpleHashMap<>();
        for (int i = 0; i < 25; i++) resizeMap.put("K" + i, i);
        resizeMap.printInternals();
        System.out.println("size after 25 puts = " + resizeMap.size());
        int missing = 0;
        for (int i = 0; i < 25; i++)
            if (resizeMap.get("K" + i) == null) missing++;
        System.out.println("missing entries = " + missing + " (should be 0)");

        System.out.println("\n========== 6. Remove all ==========");
        for (int i = 0; i < 25; i++) resizeMap.remove("K" + i);
        System.out.println("size after removing all = " + resizeMap.size());

        System.out.println("\nAll demos completed.");
    }

    /** Keys that deliberately collide — every instance has hashCode = 1. */
    static class BadHashKey {
        final int id;
        BadHashKey(int id) { this.id = id; }
        @Override public int hashCode() { return 1; }
        @Override public boolean equals(Object o) {
            return o instanceof BadHashKey && ((BadHashKey) o).id == this.id;
        }
        @Override public String toString() { return "BK" + id; }
    }
}
