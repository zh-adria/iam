import { ref, computed, unref, type Ref } from 'vue'

/**
 * 前端内存分页 composable。
 * 一次性拉取全量数据，前端切片展示（适合 < 5000 行的管理列表）。
 * 兼容 source 为 Ref<T[]> 或 () => T[] 两种写法。
 */
export function usePagination<T>(source: T[] | Ref<T[]> | (() => T[]), defaultSize = 20) {
  const page = ref(1)
  const size = ref(defaultSize)

  const getSource = () => (typeof source === 'function' ? source() : unref(source))

  const rows = computed(() => {
    const start = (page.value - 1) * size.value
    return getSource().slice(start, start + size.value)
  })
  const total = computed(() => getSource().length)

  function reset() {
    page.value = 1
  }

  return { page, size, rows, total, reset }
}
