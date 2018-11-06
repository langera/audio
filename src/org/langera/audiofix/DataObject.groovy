package org.langera.audiofix

import java.util.function.Supplier

abstract class DataObject<D extends DataObject> implements Supplier<D> {

    private static final String DELIMETER = ','
    private static final String ESCAPE = '\\'


    Set<String> aliases = new HashSet<>()
    Set<Condition> conditions = new HashSet<>()

    abstract String id()
    abstract List<String> keywordsInOrderOfImportance()
    abstract D serialize(PrintWriter out)
    abstract D unserialize(String line)

    Set<String> aliases() {
        return aliases
    }


    boolean addAliases(List<String> newAliases, Repository<D> repository) {
        boolean added = false
        newAliases.each { alias ->
            alias = Text.strongNormalize(alias)
            aliases.add(alias)
            added = added | repository.addAlias(alias, this)
        }
        return added
    }

    boolean meetsConditions(final List<String> tokens, boolean explain) {
        return conditions.inject(true, { result, condition ->
            result && condition.meetsCondition(tokens, explain)
        })
    }

    void addMustCondition(String key) {
        conditions.add(new Condition(Condition.Type.MUST, key))
    }

    void addMustNotCondition(String key) {
        conditions.add(new Condition(Condition.Type.MUST_NOT, key))
    }


    void clearConditions() {
        conditions.clear()
    }

    D addTo(Repository<D> db) {
        db.add((D) this)
        return (D) this
    }

    protected static void printDelimeter(PrintWriter out) {
        out.print(DELIMETER)
    }

    protected static String printCollection(PrintWriter out, Collection<String> strings) {
        out.print(strings.size())
        if (!strings.isEmpty()) {
            printDelimeter(out)
            out.print(joinDelimeter(strings))
        }
    }

    protected static int readCollection(String[] tokens, int index, Collection<String> strings) {
        int size = Integer.parseInt(tokens[index++])
        for (int i = 0; i < size; i++) {
            index = set(tokens, index,  { strings.add(it) })
        }
        return index
    }

    protected static String joinDelimeter(Collection<String> strings) {
        strings.join(DELIMETER)
    }

    protected static String[] splitDelimeter(String str) {
        str.split(DELIMETER)
    }

    protected static int set(String[] line, int index, Closure setter) {
        StringBuilder toSet = new StringBuilder()
        while (line[index].endsWith(ESCAPE)) {
            toSet.append(line[index]).append(DELIMETER)
            index++
        }
        setter.call(unescape(toSet.append(line[index++]).toString()))
        return index
    }

    protected static String escape(String str) {
        try {
            str.replaceAll(ESCAPE + ESCAPE, ESCAPE + ESCAPE).replaceAll(DELIMETER, ESCAPE + ESCAPE + DELIMETER)
        } catch (Exception e) {
            e.printStackTrace()
        }
    }

    protected static String unescape(String str) {
        str.replaceAll(ESCAPE + ESCAPE + ESCAPE + ESCAPE, ESCAPE + ESCAPE).replaceAll(ESCAPE + ESCAPE + DELIMETER, DELIMETER)
    }

    protected static List<String> escape(Collection<String> strings) {
        strings.collect { escape(it) }
    }
}
