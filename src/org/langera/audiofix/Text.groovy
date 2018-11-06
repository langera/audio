package org.langera.audiofix


class Text {

    static String normalize(String name) {
        if (name == null) {
            return null
        }
        name = name.replaceAll('ł', 'l')
        return java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD).replaceAll("[^\\p{ASCII}]", "").toLowerCase().trim()
    }

    static String strongNormalize(String name) {

        return normalize(name).replaceAll(/\W/, '')
    }

    static int levenshteinDistance(String left, String right) {
        int n = left.length()
        int m = right.length()

        if (n == 0) {
            return m
        } else if (m == 0) {
            return n
        }

        if (n > m) {
            // swap the input strings to consume less memory
            final CharSequence tmp = left
            left = right
            right = tmp
            n = m
            m = right.length()
        }

        final int[] p = new int[n + 1]

        int i // iterates through left
        int j // iterates through right
        int upperLeft
        int upper

        char rightJ
        int cost

        for (i = 0; i <= n; i++) {
            p[i] = i
        }

        for (j = 1; j <= m; j++) {
            upperLeft = p[0]
            rightJ = right.charAt(j - 1)
            p[0] = j

            for (i = 1; i <= n; i++) {
                upper = p[i]
                cost = left.charAt(i - 1) == rightJ ? 0 : 1
                // minimum of cell to the left+1, to the top+1, diagonally left and up +cost
                p[i] = Math.min(Math.min(p[i - 1] + 1, p[i] + 1), upperLeft + cost)
                upperLeft = upper
            }
        }

        return p[n]
    }

}
