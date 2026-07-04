import { ref, computed } from 'vue'

/**
 * 前端内存分页 composable。
 * 一次性拉取全量数据，前端切片展示（适合 < 5000 行的管理列表）。
 */
export function usePagination<T>(source: () => T[], defaultSize = 20) {
  const page = ref(1)
  const size = ref(defaultSize)

  const rows = computed(() => {
    const start = (page.value - 1) * size.value
    return source().slice(start, start + size.value)
  })
  const total = computed(() => source().length)

  function reset() {
    page.value = 1
  }

  return { page, size, rows, total, reset }
}
