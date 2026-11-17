# Code layout

- One top-level type per file (class, interface, enum, data class, sealed class, object). The file is named after the type.
- Directories: first level = type of file (e.g. `composables/`, `viewmodels/`, `data/model/`, `data/db/`, `enums/`), then by feature below it.
- Top-level functions (Composables, helpers) may share a file with the type they belong to.
