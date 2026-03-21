import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'

type Props = {
  url: string
}

const ServerPage = ({ url }: Props) => {
  const [html, setHtml] = useState<string>('')
  const [loading, setLoading] = useState(false)
  const containerRef = useRef<HTMLDivElement | null>(null)
  const pendingScripts = useRef<string[]>([])
  const pendingInline = useRef<string[]>([])
  const pendingDataScript = useRef<string | null>(null)
  const navigate = useNavigate()

  const mapToSpa = (path: string) => {
    if (path === '/' || path === '/dashboard') return '/dashboard'
    if (path.startsWith('/modules/finance')) return '/finance'
    if (path.startsWith('/modules/hrm')) return '/hrm'
    if (path.startsWith('/modules/procurement')) return '/procurement'
    if (path.startsWith('/modules/sales')) return '/sales'
    return null
  }

  useEffect(() => {
    let isMounted = true
    const load = async () => {
      setLoading(true)
      const response = await fetch(url, { credentials: 'include' })
      const text = await response.text()
      if (!isMounted) return
      const doc = new DOMParser().parseFromString(text, 'text/html')
      const main = doc.querySelector('main.main')
      if (!main) {
        window.location.href = url
        return
      }
      const scripts = Array.from(doc.querySelectorAll('script[src]'))
        .map((s) => s.getAttribute('src'))
        .filter(Boolean) as string[]
      const inlineScripts = Array.from(doc.querySelectorAll('script'))
        .filter((s) => !s.src)
        .map((s) => s.textContent || '')
      const dataScript =
        inlineScripts.find((code) => code.includes('__FLEET_DATA__')) || null
      pendingScripts.current = scripts
      pendingInline.current = inlineScripts
      pendingDataScript.current = dataScript

      setHtml(main.innerHTML)
      setLoading(false)
    }
    load()
    return () => {
      isMounted = false
    }
  }, [url])

  useEffect(() => {
    if (!html) return
    const renderDashboardFallback = () => {
      const w = window as any
      if (!w.Chart || !w.__FLEET_DATA__) return
      const data = w.__FLEET_DATA__
      const fleet = document.getElementById('fleetChart') as HTMLCanvasElement | null
      const revenue = document.getElementById('revenueChart') as HTMLCanvasElement | null
      if (!fleet || !revenue) return
      try {
        const fctx = fleet.getContext('2d')
        const rctx = revenue.getContext('2d')
        if (!fctx || !rctx) return
        const fgrad = fctx.createLinearGradient(0, 0, 0, 200)
        fgrad.addColorStop(0, 'rgba(90, 105, 216, 0.35)')
        fgrad.addColorStop(1, 'rgba(90, 105, 216, 0.02)')
        const rgrad = rctx.createLinearGradient(0, 0, 0, 200)
        rgrad.addColorStop(0, 'rgba(58, 164, 224, 0.3)')
        rgrad.addColorStop(1, 'rgba(58, 164, 224, 0.02)')

        const maxFleet = Math.max(
          ...(data.fleetDistance || []),
          ...(data.fleetEmpty || []),
          1
        )
        const maxRevenue = Math.max(...(data.revenueSeries || []), 1)
        const Chart = w.Chart
        const fleetAny = fleet as any
        const revenueAny = revenue as any
        if (fleetAny.__chartInstance) fleetAny.__chartInstance.destroy()
        if (revenueAny.__chartInstance) revenueAny.__chartInstance.destroy()
        fleetAny.__chartInstance = new Chart(fctx, {
          type: 'line',
          data: {
            labels: data.fleetLabels || [],
            datasets: [
              {
                label: 'Distance Driven (km)',
                data: data.fleetDistance || [],
                borderColor: '#5a69d8',
                backgroundColor: fgrad,
                fill: true,
                tension: 0.35,
                pointRadius: 3,
                pointBackgroundColor: '#5a69d8'
              },
              {
                label: 'Empty Miles (km)',
                data: data.fleetEmpty || [],
                borderColor: '#3aa4e0',
                backgroundColor: 'transparent',
                fill: false,
                tension: 0.35,
                pointRadius: 3,
                pointBackgroundColor: '#3aa4e0'
              }
            ]
          },
          options: {
            responsive: true,
            maintainAspectRatio: false,
            scales: {
              y: { beginAtZero: true, suggestedMax: maxFleet },
              x: { grid: { display: false } }
            },
            plugins: { legend: { display: true } }
          }
        })
        revenueAny.__chartInstance = new Chart(rctx, {
          type: 'line',
          data: {
            labels: data.revenueLabels || [],
            datasets: [
              {
                label: 'Revenue',
                data: data.revenueSeries || [],
                borderColor: '#3aa4e0',
                backgroundColor: rgrad,
                fill: true,
                tension: 0.35,
                pointRadius: 2,
                pointBackgroundColor: '#3aa4e0'
              }
            ]
          },
          options: {
            responsive: true,
            maintainAspectRatio: false,
            scales: {
              y: { beginAtZero: true, suggestedMax: maxRevenue },
              x: { grid: { display: false } }
            },
            plugins: { legend: { display: false } }
          }
        })
      } catch {
        // ignore
      }
    }

    const renderFinanceFallback = () => {
      const w = window as any
      const Chart = w.Chart
      if (!Chart) return
      const dataRoot = document.getElementById('expense-pie-data')
      const chart = document.getElementById('expense-pie')
      const legend = document.getElementById('expense-legend')
      if (dataRoot && chart && legend) {
        const points = Array.from(dataRoot.querySelectorAll('.chart-point'))
          .map((point) => ({
            label: (point as HTMLElement).dataset.label || '',
            value: parseFloat((point as HTMLElement).dataset.value || '0')
          }))
          .filter((item) => item.value > 0)
        const total = points.reduce((sum, item) => sum + item.value, 0)
        if (total) {
          const canvas =
            chart.querySelector('canvas') || document.createElement('canvas')
          if (!canvas.parentElement) {
            chart.innerHTML = ''
            chart.appendChild(canvas)
          }
          const labels = points.map((item) => item.label)
          const values = points.map((item) => item.value)
          const colors = [
            '#3aa4e0',
            '#8fd3f4',
            '#7c3aed',
            '#c8b5ff',
            '#b24b4b',
            '#8b8fe5'
          ]
          if ((chart as any).__chartInstance) {
            ;(chart as any).__chartInstance.destroy()
          }
          ;(chart as any).__chartInstance = new Chart(
            (canvas as HTMLCanvasElement).getContext('2d'),
            {
              type: 'doughnut',
              data: {
                labels,
                datasets: [
                  {
                    data: values,
                    backgroundColor: colors.slice(0, values.length),
                    borderWidth: 0
                  }
                ]
              },
              options: { cutout: '60%', plugins: { legend: { display: false } } }
            }
          )
          legend.innerHTML = ''
          points.forEach((item, index) => {
            const percent = Math.round((item.value / total) * 100)
            const legendItem = document.createElement('li')
            legendItem.innerHTML = `
              <span class="dot" style="background: ${
                colors[index % colors.length]
              }"></span>
              ${item.label} (${percent}%)
            `
            legend.appendChild(legendItem)
          })
        }
      }

      const seriesRoot = document.getElementById('income-expense-data')
      const seriesCanvas = document.getElementById(
        'income-expense-chart'
      ) as HTMLCanvasElement | null
      if (seriesRoot && seriesCanvas) {
        const points = Array.from(seriesRoot.querySelectorAll('.chart-point')).map(
          (point) => ({
            label: (point as HTMLElement).dataset.label || '',
            income: parseFloat((point as HTMLElement).dataset.income || '0'),
            expense: parseFloat((point as HTMLElement).dataset.expense || '0')
          })
        )
        if (points.length) {
        const seriesAny = seriesCanvas as any
        if (seriesAny.__chartInstance) {
          seriesAny.__chartInstance.destroy()
        }
        seriesAny.__chartInstance = new Chart(
            seriesCanvas.getContext('2d'),
            {
              type: 'line',
              data: {
                labels: points.map((p) => p.label),
                datasets: [
                  {
                    label: 'Income',
                    data: points.map((p) => p.income),
                    borderColor: '#3aa4e0',
                    backgroundColor: 'rgba(58, 164, 224, 0.12)',
                    tension: 0.35,
                    borderWidth: 2.4,
                    pointRadius: 0,
                    fill: true
                  },
                  {
                    label: 'Expenses',
                    data: points.map((p) => p.expense),
                    borderColor: '#7c3aed',
                    backgroundColor: 'rgba(124, 58, 237, 0.12)',
                    tension: 0.35,
                    borderWidth: 2.4,
                    pointRadius: 0,
                    fill: true
                  }
                ]
              },
              options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: { legend: { display: false } },
                scales: { x: { grid: { display: false } } }
              }
            }
          )
        }
      }
    }
    const triggerInit = () => {
      if (!document.querySelector('script[data-chartjs]')) {
        const tag = document.createElement('script')
        tag.src = '/js/vendor/chart.umd.min.js'
        tag.async = true
        tag.dataset.chartjs = 'true'
        document.head.appendChild(tag)
      }
      const replaceCanvas = (id: string) => {
        const old = document.getElementById(id) as HTMLCanvasElement | null
        if (!old || !old.parentElement) return
        const canvas = document.createElement('canvas')
        canvas.id = id
        canvas.className = old.className
        const aria = old.getAttribute('aria-label')
        if (aria) canvas.setAttribute('aria-label', aria)
        const role = old.getAttribute('role')
        if (role) canvas.setAttribute('role', role)
        old.parentElement.replaceChild(canvas, old)
      }
      replaceCanvas('fleetChart')
      replaceCanvas('revenueChart')
      replaceCanvas('income-expense-chart')
      const pie = document.getElementById('expense-pie')
      if (pie) pie.innerHTML = ''
      const dataScript = pendingDataScript.current
      if (dataScript) {
        try {
          // Re-evaluate data script to refresh window.__FLEET_DATA__
          // eslint-disable-next-line no-new-func
          new Function(dataScript)()
        } catch {
          try {
            // eslint-disable-next-line no-eval
            ;(0, eval)(dataScript)
          } catch {
            // ignore
          }
        }
      }
      try {
        window.dispatchEvent(new Event('spa:content'))
      } catch {
        // ignore
      }
      const w = window as unknown as {
        tryInitCharts?: () => void
        renderExpensePie?: () => void
        renderIncomeExpenseChart?: () => void
      }
      const runOnce = () => {
        if (typeof w.tryInitCharts === 'function') w.tryInitCharts()
        if (typeof w.renderExpensePie === 'function') w.renderExpensePie()
        if (typeof w.renderIncomeExpenseChart === 'function') w.renderIncomeExpenseChart()
      }
      let attempts = 0
      const maxAttempts = 18
      const retry = () => {
        attempts += 1
        runOnce()
        if (attempts === 2) {
          renderDashboardFallback()
          renderFinanceFallback()
        }
        const fleetCanvas = document.getElementById('fleetChart') as any
        const revenueCanvas = document.getElementById('revenueChart') as any
        const incomeCanvas = document.getElementById('income-expense-chart') as any
        const pie = document.getElementById('expense-pie')
        const hasDashboardCharts =
          (fleetCanvas && fleetCanvas.__chartInstance) ||
          (revenueCanvas && revenueCanvas.__chartInstance)
        const hasFinanceCharts =
          (incomeCanvas && incomeCanvas.__chartInstance) ||
          (pie && pie.querySelector('canvas'))
        if (hasDashboardCharts || hasFinanceCharts) {
          return
        }
        if (attempts < maxAttempts) {
          setTimeout(retry, 250)
        }
      }
      retry()
    }

    const runScripts = () => {
      const scripts = pendingScripts.current
      const inline = pendingInline.current
      pendingScripts.current = []
      pendingInline.current = []

      for (const src of scripts) {
        if (!document.querySelector(`script[src="${src}"]`)) {
          const tag = document.createElement('script')
          tag.src = src
          tag.onload = () => triggerInit()
          document.body.appendChild(tag)
        }
      }
      inline.forEach((code) => {
        const tag = document.createElement('script')
        tag.textContent = `(function(){\n${code}\n})();`
        document.body.appendChild(tag)
      })
      setTimeout(triggerInit, 50)
    }

    requestAnimationFrame(() => {
      requestAnimationFrame(runScripts)
    })
  }, [html])

  useEffect(() => {
    const root = containerRef.current
    if (!root) return
    const onClick = (event: MouseEvent) => {
      if (event.defaultPrevented || event.button !== 0) return
      const target = event.target as HTMLElement | null
      const anchor = target?.closest('a') as HTMLAnchorElement | null
      if (!anchor || anchor.target || anchor.hasAttribute('download')) return
      const href = anchor.getAttribute('href')
      if (!href || href.startsWith('http') || href.startsWith('mailto:')) return
      const urlObj = new URL(href, window.location.origin)
      const next = mapToSpa(urlObj.pathname)
      if (!next) return
      event.preventDefault()
      navigate(next)
    }
    root.addEventListener('click', onClick)
    return () => root.removeEventListener('click', onClick)
  }, [navigate])

  return (
    <div
      ref={containerRef}
      className={loading ? 'server-page is-loading' : 'server-page'}
      dangerouslySetInnerHTML={{ __html: html }}
    />
  )
}

export default ServerPage
