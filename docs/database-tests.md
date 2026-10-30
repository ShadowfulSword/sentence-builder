# Database integrity tests (issue #24)

`DatabaseManagerIT` tests the existing `DatabaseManager` against a real MySQL
database. It checks stored counts, batch writes (including more than 5,000 rows),
both transition foreign keys, cascading word renames, transaction rollback, and
imported-file metadata/uniqueness. Assertions use a separate JDBC connection to
verify that changes were committed.

These tests exercise the database layer. They do not verify the text parser,
GUI, or whether reimporting an entire book is idempotent.


Troubleshooting 

For connection errors, check that MySQL is
running on the configured port. For access errors, check the environment
variables in the same terminal. For missing-table or unknown-column errors,
check that the test database matches the current repository schema.


