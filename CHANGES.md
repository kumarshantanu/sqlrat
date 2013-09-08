# TODO and Changes


## TODO

* [TODO] Join-based SQL statements (avoid N+1 operations)
   * [TODO] find-rels - given A, [D col1 col2] infer JOIN A..B..C..D (many to many)
   * [TODO] insert-rels - maybe "INSERT SELECT"
   * [TODO] update-rels
   * [TODO] delete-rels


## Changelog

### Version 0.3.0 / 2013-Sep-??

* SQL template
   * Symbols are database identifiers, substituted by values
   * Keywords are value placeholders, replaced by `?`
   * Substitution fn for symbols - e.g. `emp` can become `'emp'`
   * Placeholder fn for keywords - e.g. `?` can become `?::integer` for ints
   * Partial template realization (symbols only)
   * Parser - e.g. turn `"WHERE code = :code"` into `["WHERE code =" :code]`
* Entity
   * Entity metadata (optional keys: `:table` `:id`)
   * Column metadata (optional keys: `:colname` `:insert?` `:default`)
   * CRUD functions (by columns and ID, using symbol-substituted identifiers)
   * Optional map args - distinct, cols, table, where-cols, order-by
* Entity relation
   * Relation mapping (adjacency list) definition
   * Value induction from related entity instances

