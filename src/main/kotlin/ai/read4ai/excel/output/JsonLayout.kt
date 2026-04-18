package ai.read4ai.excel.output

import ai.read4ai.excel.ExperimentalRead4ai

/** Controls the JSON table layout used by [JsonFormatter]. */
enum class JsonLayout {
    /** 2D string arrays with sparse merge list. Default. */
    COMPACT,
    /** Row objects with inline merge info: `{"row": N, "cells": [...]}`. Experimental. */
    @ExperimentalRead4ai
    ROW_OBJECT,
}
