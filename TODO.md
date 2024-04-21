- Implement and test basic Cache abilities:
  - Drop item(s) when over the maximum size.
    - check flag to verify access or update timeout order.
  - Drop item(s) when access timeout passed.
  - Drop item(s) when update timeout passed.

- Test (and fix) "unhappy path" errors:
  - removing a key that doesn't exist

- Test thread safety. (how?)

- Implement and test CacheManager
  - accurate list of caches
  - request cache
  - request cache twice with different classes
  - add and remove configurations
    - verify register and unregister protocols

- Add CacheConfiguration managers to
  - configure from a file
  - reconfigure whenever the file changes
  - use a socket and simple language to query and modify cache parameters.
  - expose the caches through the Management Interface as MBeans

- Documentation
  - Ensure all interfaces are at least passably documented.
