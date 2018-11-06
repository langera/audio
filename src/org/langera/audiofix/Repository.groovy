package org.langera.audiofix


import java.util.function.BiFunction
import java.util.function.Function
import java.util.function.Supplier

import static org.langera.audiofix.Text.strongNormalize

class Repository<D extends DataObject<D>> {

    public static final String DATA_FIX_CONFIG = ' -> '
    public static final String CONDITIONS_CONFIG = ' = '
    public static final String ALIAS_CONFIG = ' @ '
    public static final String ADDITION_CONFIG = '+'
    public static final String SEARCH_VARIANTS_CONFIG = '?'
    public static final String SEARCH_VARIANTS_KEYWORD_SEPARATOR = ':'
    public static final String SEARCH_VARIANTS_SEPARATOR = ','
    public static final String MUST_NOT_CONDITION = '-'
    public static final String MUST_CONDITION = '+'
    private Map<String, List<D>> db = [:]
    private Map<String, D> aliasDb = [:]
    private Map<String, Set<String>> searchVariants = [:]
    private D instance

    Repository(final Supplier<D> supplier) {
        this.instance = supplier.get()
    }

    Set<D> findAll(String key) {
        boolean explain = false
        if (key.startsWith('explain:')) {
            explain = true
            key = key.substring('explain:'.length())
        }
        List<String> tokens = key.split(/\s|\\|\/|,|;|:|\[|\]|\d|\(\)|-/).collect { Text.strongNormalize(it) }
        D data = (tokens.size() == 1) ? findExactMatch(tokens[0], (explain)?' ':null) : findExactMatch(tokens.join(''), (explain)?'concat':null)
        if (data) {
            return [ data ]
        }
        tokens = tokens.collect() { it.trim() }.findAll { it.length() > 1 }
        Map<String, Set<D>> candidatesByToken = [:]
        tokens.each { token ->
            findDataForToken(token, token, candidatesByToken, (explain)?' ':null)
            Set<String> variants = searchVariants.get(token)
            if (variants) {
                variants.each {
                    findDataForToken(it, token, candidatesByToken, (explain)?'variant':null)
                }
            }
        }

        Map<D, Integer> count = [:]
        candidatesByToken.values().each { candidates ->
            candidates.each { candidate ->
                count.compute(candidate, { k, v -> v ? v + 1 : 1})
            }
        }

        int max = count.values().max()
        if (explain) {
            count.each { k, v ->
                println "$v: ${k.id()}"
            }
            println "max: $max"
        }
        Set<D> overallCandidates = new HashSet<>()
        count.each { k, v ->
            if (v == max) {
                overallCandidates.add(k)
            }
        }

        return overallCandidates.findAll( ) { candidate ->
            candidate.meetsConditions(tokens, explain)
        }
    }

    private void findDataForToken(String token, String key, Map<String, Set<D>> candidatesByToken, String explain) {
        db.each { k, v ->
            if (k.contains(token)) {
                candidatesByToken.compute(key, { t, results ->
                    if (!results) {
                        results = new HashSet<D>()
                    }
                    results.addAll(v)
                    return results
                })
                if (explain) {
                    println "db $explain $token - ${v.collect {it.id()}}"
                }
            }
        }
        aliasDb.each { k, v ->
            if (k == token) {
                candidatesByToken.compute(key, { t, results ->
                    if (!results) {
                        results = new HashSet<D>()
                    }
                    results.add(v)
                    return results
                })
                candidatesByToken.compute("ALIAS:$key", { t, results ->
                    if (!results) {
                        results = new HashSet<D>()
                    }
                    results.add(v)
                    return results
                })
                if (explain) {
                    println "alias $explain $token - ${v.id()}"
                }
            }
        }
    }

    private D findExactMatch(final String key, String explain) {
        D data = db.containsKey(key) && db.get(key).size() == 1 ? db.get(key)[0] : null
        if (explain && data) {
            println "exact match $explain: $key - ${data.id()}"
        }
        if (!data) {
            data = aliasDb.get(key)
            if (explain && data) {
                println "alias exact match $explain: $key - ${data.id()}"
            }
        }
        return data
    }

    boolean load(File dbFile) {
        if (dbFile.exists()) {
            dbFile.withReader { input ->
                input.eachLine { line ->
                    if (line.startsWith(SEARCH_VARIANTS_CONFIG)) {
                        addSearchVariants(line)
                    }
                    else {
                        instance.get().unserialize(line).addTo(this)
                    }
                }
                db.values().flatten().each { D data ->
                    data.aliases().each {
                        addAlias(it, data)
                    }
                }
            }
            return true
        }
        return false
    }

    void store(File dbFile) {
        dbFile.withPrintWriter { out ->
            values().each { v ->
                v.each {
                    it.serialize(out)
                }
            }
            searchVariants.each { k, variants ->
                out.println("$SEARCH_VARIANTS_CONFIG$k$SEARCH_VARIANTS_KEYWORD_SEPARATOR${variants.join(SEARCH_VARIANTS_SEPARATOR)}")
            }
        }
    }

    boolean isEmpty() {
        db.isEmpty()
    }

    int size() {
        (int) db.values().inject(0, { i, l -> i + l.size() })
    }

    boolean addAlias(String alias, D data) {
        D prev = aliasDb.putIfAbsent(Text.strongNormalize(alias), data)
        if (prev && prev != data) {
            throw new IllegalStateException("matching alias ${Text.strongNormalize(alias)} for $prev and $data");
        }
        return prev == null
    }

    List<D> compute(String key, BiFunction<String, List<D>, List<D>> remappingFunction)  {
        db.compute(key, remappingFunction)
    }

    List<String> keys() {
        db.keySet().asList()
    }

    List<List<D>> values() {
        db.values().asList()
    }

    List<D> remove(String key) {
        return db.remove(key)
    }

    List<D> removeByValue(D value) {
        List<D> data = db.get(value.id())
        data.remove(value)
        if (!data.isEmpty()) {
            db.put(value.id(), data)
        }
        return data
    }

    void add(final D data) {
        compute(data.id(), { k, List<D> v ->  (v == null) ? [data] : v + data })
    }

    boolean addIfAbsent(final D data) {
        if (!db.containsKey(data.id())) {
            compute(data.id(), { k, List<D> v -> (v == null) ? [data] : v + data })
            return true
        }
        return false
    }

    void patchSearch() {
        db.values().flatten().collect { D data ->
            Set<String> differentiators = new HashSet<>()
            List<String> keywords = data.keywordsInOrderOfImportance()
            (1..keywords.size()).each { len ->
                Set<D> searchResults = findAll(keywords.subList(0, len).join(' '))
                if (searchResults.size() > 1) {
                    searchResults.each { conflictingData ->
                        if (conflictingData != data) {
                            differentiators.addAll(conflictingData.keywordsInOrderOfImportance().findAll { !data.id().contains(it) })
                        }
                    }
                }
            }
            addDifferentiators(data, differentiators)
        }
    }

    boolean applyConfig(String line,
                     Function<String, D> adder,
                     BiFunction<String[], D, Boolean> fixer) {
        if (line.contains(DATA_FIX_CONFIG)) {
            return fixData(line.split(DATA_FIX_CONFIG), fixer)
        }
        if (line.contains(ALIAS_CONFIG)) {
            String[] aliasDef = line.split(ALIAS_CONFIG)
            return addAliasFromConfig(aliasDef[0], aliasDef[1])
        }
        if (line.contains(CONDITIONS_CONFIG)) {
            String[] conditionsDef = line.split(CONDITIONS_CONFIG)
            return addConditionsFromConfig(conditionsDef[0], conditionsDef[1])
        }
        if (line.startsWith(ADDITION_CONFIG)) {
            return addIfAbsent(adder.apply(line.substring(1).trim()))
        }
        if (line.startsWith(SEARCH_VARIANTS_CONFIG)) {
            return addSearchVariants(line)
        }
    }

    private boolean addConditionsFromConfig(final String id, final String conditions) {
        List<D> data = db.get(id)
        boolean changed = false
        if (data && data.size() == 1) {
            conditions.split(',').collect() {it.trim() }.each { condition ->
                if (condition.startsWith(MUST_NOT_CONDITION)) {
                    data[0].addMustNotCondition(condition.substring(1))
                    changed = true
                }
                else if (condition.startsWith(MUST_CONDITION)) {
                    data[0].addMustCondition(condition.substring(1))
                    changed = true
                }
                else {
                    throw new UnsupportedOperationException("Condition: $condition for $id")
                }
            }
        }
        return changed
    }

    void clearConditions() {
        db.values().flatten().each { D data ->
            data.clearConditions()
        }
    }

    private boolean addAliasFromConfig(final String id, final String aliases) {
        List<D> data = db.get(id)
        if (data && data.size() == 1) {
            return data[0].addAliases(aliases.split(',').collect { it.trim()}, this)
        }
        return false
    }

    private boolean addSearchVariants(String line) {
        int sep = line.indexOf(SEARCH_VARIANTS_KEYWORD_SEPARATOR)
        String from = Text.strongNormalize(line.substring(1, sep))
        List<String> variants = line.substring(sep + 1).split(SEARCH_VARIANTS_SEPARATOR).collect() { Text.strongNormalize(it) }
        Set<String> prev = searchVariants.get(from)
        boolean change = prev ? !prev.containsAll(variants) : true
        searchVariants.compute(from, { k, v -> (v == null) ? new HashSet<String>(variants) : v + variants    })
        variants.each { variant ->
            searchVariants.compute(variant, { k, v -> (v == null) ? new HashSet<String>([from]) : v + [from]    })
        }
        return change
    }

    private boolean fixData(String[] replacement, BiFunction<String[], D, Boolean> fixer) {
        String key = strongNormalize(replacement[0])
        boolean result = false
        values().flatten().each { D data ->
            if (data.id().contains(key)) {
                result = result | fixer.apply(replacement, data)
            }
        }
        return result
    }


    void report() {
        println "DB size: ${db.size()}"
        db.each { k, v ->
            if (v.size() > 1) {
                println "DB double: $k : ${v.collect({ "($it.years)" })}"
            }
        }
    }

    private static void addDifferentiators(D data, Set<String> keywords) {
        if (!keywords.isEmpty()) {
            keywords.each {
                data.addMustNotCondition(it)
            }
            println "MUST NOT CONDITION ${data.id()} -> $keywords"
        }
    }
}
