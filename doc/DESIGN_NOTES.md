## `com.frank_mitchell`

The only domain under my control is frank-mitchell.com. Since Java
doesn't allow dashes in identifiers, I had to substitute a `_`.


## DateTimeValue

Unlike `java.util.Date` users can set the value of a Date object.
I wanted something with value semantics, e.g. `java.lang.Long`.

## Duration

Like [DateTimeValue](#datetimevalue) this object represents a length of
time with value semantics: once created its internal value never changes.
All the following should be valid Duration strings:

- "2s" &lArr; 2000 (ms)
" "2m" &lArr; 120000 (ms)
- "3h" &lArr; 
