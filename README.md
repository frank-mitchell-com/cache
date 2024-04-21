This project is a recreation of a simple in-memory caching library I created
for a previous job.

It has a number of inherent limitations:

- It does not integrate with JSR 107 (`javax.cache`) or any other standard API.
- It does not store data in a database or other backing store.
- It does not distribute data among remote instances.

It *will* (when complete) do the following:
- Allow system administrators to control the parameters of each cache.
- Provide an almost realtime view of all data being cached.

So, if you need a simple way to cache results that *almost* looks like
a `java.util.Map` then this might fit the bill.

## Building the library

Run `ant` in the project's root directory.  It will build a jar file (currently
`cache.jar`) after running unit tests and coverage metrics.
