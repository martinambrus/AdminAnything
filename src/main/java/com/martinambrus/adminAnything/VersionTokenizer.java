package com.martinambrus.adminAnything;

// source: https://stackoverflow.com/a/10034633/467164

/**
 * Tokenizes version number, so it can be compared by {@link com.martinambrus.adminAnything.VersionComparator}.
 */
public class VersionTokenizer {
    /**
     * Internally stores original version string.
     */
    private final String _versionString;

    /**
     * Internally stores original version string length.
     */
    private final int _length;

    /**
     * Internally stores current tokenization position.
     */
    private int _position;

    /**
     * Internally stores last tokenization number value for comparisons.
     */
    private int _number;

    /**
     * Internally stores version suffix after the numeric version part.
     */
    private String _suffix;

    /**
     * Will be TRUE while there is still something to tokenize, will become FALSE otherwise.
     */
    private boolean _hasValue;

    /**
     * Getter for the internal _number variable.
     * @return The internal _number variable.
     */
    public int getNumber() {
        return _number;
    } // end method

    /**
     * Getter for the internal _suffix variable.
     * @return The internal _suffix variable.
     */
    public String getSuffix() {
        return _suffix;
    } // end method

    /**
     * Getter for the internal _hasValue variable.
     * @return The internal _hasValue variable.
     */
    public boolean hasValue() {
        return _hasValue;
    } // end method

    /**
     * VersionTokenizer constructor.
     *
     * @param versionString The version string to tokenize.
     */
    public VersionTokenizer(String versionString) {
        if (versionString == null)
            throw new IllegalArgumentException("versionString is null");

        _versionString = versionString;
        _length = versionString.length();
    } // end method

    /**
     * Moves on to the next string position to be tokenized.
     *
     * @return TRUE if there are no more characters to move on to, FALSE otherwise.
     */
    public boolean MoveNext() {
        _number = 0;
        _suffix = "";
        _hasValue = false;

        // No more characters
        if (_position >= _length)
            return false;

        _hasValue = true;

        while (_position < _length) {
            char c = _versionString.charAt(_position);
            if (c < '0' || c > '9') break;
            _number = _number * 10 + (c - '0');
            _position++;
        }

        int suffixStart = _position;

        while (_position < _length) {
            char c = _versionString.charAt(_position);
            if (c == '.') break;
            _position++;
        }

        _suffix = _versionString.substring(suffixStart, _position);

        if (_position < _length) _position++;

        return true;
    } // end method

} // end class