## Implementation

- Better algorithm for relocating least recently used entries, 
  e.g. linked list between EntryRecord.

## Testing

- Test all methods on Cache
  - Verify with specifications in javax.cache.Cache and java.util.Map.

- Test (and fix) "unhappy path" errors:
  - removing a key that doesn't exist

- Test thread safety. (how?)

- Test CacheManager
  - accurate list of caches
  - request cache
  - request cache twice with different classes
  - add and remove configurations
    - verify register and unregister protocols

## Functionality

- Entry point for CacheManager, along the lines of `javax.cache`.

- Add CacheConfiguration managers to
  - configure from a file
  - reconfigure whenever the file changes
  - use a socket and simple language to query and modify cache parameters.
  - expose the caches through the Management Interface as MBeans

## Documentation

- Ensure all interfaces are at least passably documented.
