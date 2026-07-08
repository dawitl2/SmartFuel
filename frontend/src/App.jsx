import { useEffect, useMemo, useState } from 'react'
import {
  Activity,
  AlertTriangle,
  BarChart3,
  Bell,
  Car,
  CheckCircle2,
  Droplets,
  Gauge,
  History,
  Moon,
  Settings,
  Sun,
  Thermometer,
  Timer,
  Wrench,
  Zap,
} from 'lucide-react'
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { createRefuelEvent, getDashboard } from './api'

function App() {
  const [dashboard, setDashboard] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [theme, setTheme] = useState('dark')
  const [refuelLiters, setRefuelLiters] = useState(5)
  const [savingRefuel, setSavingRefuel] = useState(false)

  async function loadDashboard() {
    try {
      setError('')
      const data = await getDashboard()
      setDashboard(data)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadDashboard()
  }, [])

  useEffect(() => {
    document.documentElement.classList.toggle('dark', theme === 'dark')
  }, [theme])

  const metrics = useMemo(() => {
    if (!dashboard) return []

    return [
      {
        label: 'Fuel Remaining',
        value: `${dashboard.fuel.fuelRemainingLiters} L`,
        detail: `${dashboard.fuel.fuelPercentage}% tank`,
        icon: Droplets,
        tone: 'emerald',
      },
      {
        label: 'Range',
        value: `${dashboard.fuel.estimatedRangeKm} km`,
        detail: 'Estimated driving range',
        icon: Gauge,
        tone: 'blue',
      },
      {
        label: 'Speed',
        value: `${dashboard.liveStatus.speedKph} km/h`,
        detail: `Max ${dashboard.liveStatus.maxSpeedKph} km/h today`,
        icon: Zap,
        tone: 'amber',
      },
      {
        label: 'RPM',
        value: dashboard.liveStatus.rpm.toLocaleString(),
        detail: `Avg ${dashboard.liveStatus.averageRpm.toLocaleString()} rpm`,
        icon: Activity,
        tone: 'rose',
      },
    ]
  }, [dashboard])

  async function handleRefuel(eventType) {
    setSavingRefuel(true)
    try {
      await createRefuelEvent({
        eventType,
        litersAdded: eventType === 'full_reset' ? 40 : Number(refuelLiters),
        note: eventType === 'full_reset' ? 'Dashboard full tank reset.' : 'Dashboard partial refuel.',
      })
      await loadDashboard()
    } catch (err) {
      setError(err.message)
    } finally {
      setSavingRefuel(false)
    }
  }

  if (loading) return <LoadingScreen />
  if (error && !dashboard) return <ErrorScreen message={error} onRetry={loadDashboard} />

  return (
    <main className="min-h-screen bg-zinc-100 text-zinc-950 transition-colors dark:bg-neutral-950 dark:text-zinc-50">
      <div className="flex min-h-screen">
        <aside className="hidden w-72 border-r border-zinc-200 bg-white px-5 py-6 dark:border-white/10 dark:bg-neutral-900 lg:block">
          <Brand />
          <nav className="mt-8 space-y-1">
            {[
              ['Dashboard', Gauge],
              ['Live Status', Activity],
              ['Trip History', History],
              ['Fuel Estimation', Droplets],
              ['Statistics', BarChart3],
              ['Vehicle Health', CheckCircle2],
              ['Maintenance', Wrench],
              ['Notifications', Bell],
              ['Settings', Settings],
            ].map(([label, Icon], index) => (
              <a
                className={`flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium ${
                  index === 0
                    ? 'bg-zinc-950 text-white dark:bg-white dark:text-zinc-950'
                    : 'text-zinc-600 hover:bg-zinc-100 dark:text-zinc-300 dark:hover:bg-white/10'
                }`}
                href={`#${label.toLowerCase().replaceAll(' ', '-')}`}
                key={label}
              >
                <Icon size={18} />
                {label}
              </a>
            ))}
          </nav>
        </aside>

        <section className="flex min-w-0 flex-1 flex-col">
          <header className="sticky top-0 z-10 border-b border-zinc-200 bg-zinc-100/85 px-4 py-4 backdrop-blur dark:border-white/10 dark:bg-neutral-950/85 sm:px-6 xl:px-8">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.18em] text-emerald-600 dark:text-emerald-400">
                  SmartFuel Fleet OS
                </p>
                <h1 className="mt-1 text-2xl font-semibold tracking-normal sm:text-3xl">
                  {dashboard.car.name}
                </h1>
              </div>
              <div className="flex items-center gap-2">
                <StatusPill status={dashboard.car.status} />
                <button
                  aria-label="Toggle theme"
                  className="inline-flex size-10 items-center justify-center rounded-md border border-zinc-200 bg-white text-zinc-700 shadow-sm dark:border-white/10 dark:bg-white/10 dark:text-zinc-100"
                  onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
                  type="button"
                >
                  {theme === 'dark' ? <Sun size={18} /> : <Moon size={18} />}
                </button>
              </div>
            </div>
          </header>

          <div className="space-y-6 p-4 sm:p-6 xl:p-8">
            {error ? (
              <div className="rounded-md border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-900 dark:border-amber-400/30 dark:bg-amber-400/10 dark:text-amber-100">
                {error}
              </div>
            ) : null}

            <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-4" id="dashboard">
              {metrics.map((metric) => (
                <MetricCard key={metric.label} {...metric} />
              ))}
            </section>

            <section className="grid gap-6 xl:grid-cols-[1.15fr_0.85fr]">
              <Panel id="live-status" title="Live Vehicle Status" icon={Car}>
                <div className="grid gap-4 sm:grid-cols-2">
                  <Readout label="Vehicle" value={`${dashboard.car.year} ${dashboard.car.make} ${dashboard.car.model}`} />
                  <Readout label="Estimated Odometer" value={`${dashboard.liveStatus.estimatedOdometerKm.toLocaleString()} km`} />
                  <Readout label="Coolant" value={`${dashboard.liveStatus.coolantTempC} C`} icon={Thermometer} />
                  <Readout label="Engine Load" value={`${dashboard.liveStatus.engineLoadPercent}%`} icon={Activity} />
                  <Readout label="Trip Distance" value={`${dashboard.liveStatus.tripDistanceKm} km`} />
                  <Readout label="Driving Time" value={formatDuration(dashboard.liveStatus.drivingSeconds)} icon={Timer} />
                </div>
              </Panel>

              <Panel id="fuel-estimation" title="Fuel Estimation" icon={Droplets}>
                <div className="flex flex-col gap-5">
                  <FuelRing percentage={dashboard.fuel.fuelPercentage} />
                  <div className="grid grid-cols-2 gap-3 text-sm">
                    <Readout label="Used" value={`${dashboard.fuel.fuelUsedLiters} L`} />
                    <Readout label="Rate" value="0.1 L/km" />
                  </div>
                  <div className="flex flex-col gap-3 sm:flex-row">
                    <button
                      className="inline-flex flex-1 items-center justify-center gap-2 rounded-md bg-zinc-950 px-4 py-2.5 text-sm font-semibold text-white disabled:opacity-50 dark:bg-white dark:text-zinc-950"
                      disabled={savingRefuel}
                      onClick={() => handleRefuel('full_reset')}
                      type="button"
                    >
                      <Droplets size={17} />
                      Full Tank Reset
                    </button>
                    <div className="flex flex-1 overflow-hidden rounded-md border border-zinc-200 bg-white dark:border-white/10 dark:bg-white/10">
                      <input
                        aria-label="Partial refuel liters"
                        className="min-w-0 flex-1 bg-transparent px-3 text-sm outline-none"
                        max="40"
                        min="1"
                        onChange={(event) => setRefuelLiters(event.target.value)}
                        type="number"
                        value={refuelLiters}
                      />
                      <button
                        className="border-l border-zinc-200 px-3 text-sm font-semibold disabled:opacity-50 dark:border-white/10"
                        disabled={savingRefuel}
                        onClick={() => handleRefuel('partial_refuel')}
                        type="button"
                      >
                        Add L
                      </button>
                    </div>
                  </div>
                </div>
              </Panel>
            </section>

            <section className="grid gap-6 xl:grid-cols-2">
              <ChartPanel data={dashboard.charts} dataKey="speed" id="speed-chart" stroke="#2563eb" title="Speed Over Time" unit="km/h" />
              <ChartPanel data={dashboard.charts} dataKey="rpm" id="rpm-chart" stroke="#e11d48" title="RPM Over Time" unit="rpm" />
              <AreaPanel data={dashboard.charts} id="fuel-history" title="Fuel Estimate History" />
              <DailyDistancePanel data={dashboard.statistics.dailyDistance} />
            </section>

            <section className="grid gap-6 xl:grid-cols-[1fr_0.85fr]" id="trip-history">
              <Panel title="Trip History" icon={History}>
                <div className="overflow-x-auto">
                  <table className="min-w-full text-left text-sm">
                    <thead className="text-xs uppercase text-zinc-500 dark:text-zinc-400">
                      <tr>
                        <th className="py-3 pr-4">Started</th>
                        <th className="py-3 pr-4">Distance</th>
                        <th className="py-3 pr-4">Avg Speed</th>
                        <th className="py-3 pr-4">Fuel</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-zinc-200 dark:divide-white/10">
                      {dashboard.trips.map((trip) => (
                        <tr key={trip.id}>
                          <td className="py-3 pr-4">{formatDate(trip.startedAt)}</td>
                          <td className="py-3 pr-4">{trip.distanceKm} km</td>
                          <td className="py-3 pr-4">{trip.averageSpeedKph} km/h</td>
                          <td className="py-3 pr-4">{trip.fuelUsedLiters} L</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </Panel>

              <Panel id="notifications" title="Notifications" icon={Bell}>
                <div className="space-y-3">
                  {dashboard.notifications.map((item) => (
                    <div className="rounded-md border border-zinc-200 bg-zinc-50 p-3 dark:border-white/10 dark:bg-white/5" key={item.id}>
                      <div className="flex items-start gap-3">
                        <AlertTriangle className="mt-0.5 text-amber-500" size={18} />
                        <div>
                          <p className="font-semibold">{item.title}</p>
                          <p className="mt-1 text-sm text-zinc-600 dark:text-zinc-400">{item.body}</p>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </Panel>
            </section>

            <section className="grid gap-6 xl:grid-cols-2">
              <Panel id="maintenance" title="Maintenance" icon={Wrench}>
                <div className="space-y-3">
                  {dashboard.maintenance.map((item) => (
                    <div className="flex items-center justify-between gap-4 rounded-md border border-zinc-200 bg-zinc-50 p-3 dark:border-white/10 dark:bg-white/5" key={item.id}>
                      <div>
                        <p className="font-semibold">{item.title}</p>
                        <p className="text-sm text-zinc-600 dark:text-zinc-400">{item.description}</p>
                      </div>
                      <span className="rounded-md bg-emerald-100 px-2.5 py-1 text-xs font-semibold text-emerald-700 dark:bg-emerald-400/10 dark:text-emerald-300">
                        {item.status}
                      </span>
                    </div>
                  ))}
                </div>
              </Panel>

              <Panel id="driving-statistics" title="Driving Habits" icon={Activity}>
                <div className="grid gap-3 sm:grid-cols-2">
                  {dashboard.statistics.habits.map((habit) => (
                    <div className="rounded-md bg-zinc-50 p-3 dark:bg-white/5" key={habit.label}>
                      <div className="flex items-center justify-between text-sm">
                        <span>{habit.label}</span>
                        <span className="font-semibold">{habit.value}%</span>
                      </div>
                      <div className="mt-3 h-2 rounded-full bg-zinc-200 dark:bg-white/10">
                        <div className="h-full rounded-full bg-emerald-500" style={{ width: `${habit.value}%` }} />
                      </div>
                    </div>
                  ))}
                </div>
              </Panel>
            </section>
          </div>
        </section>
      </div>
    </main>
  )
}

function Brand() {
  return (
    <div className="flex items-center gap-3">
      <div className="flex size-11 items-center justify-center rounded-md bg-zinc-950 text-white dark:bg-white dark:text-zinc-950">
        <Gauge size={22} />
      </div>
      <div>
        <p className="text-lg font-semibold">SmartFuel</p>
        <p className="text-xs text-zinc-500 dark:text-zinc-400">Vehicle telemetry</p>
      </div>
    </div>
  )
}

function StatusPill({ status }) {
  return (
    <span className="inline-flex items-center gap-2 rounded-md border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm font-semibold text-emerald-700 dark:border-emerald-400/20 dark:bg-emerald-400/10 dark:text-emerald-300">
      <span className="size-2 rounded-full bg-emerald-500" />
      {status}
    </span>
  )
}

function MetricCard({ detail, icon: Icon, label, tone, value }) {
  const tones = {
    emerald: 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-300',
    blue: 'bg-blue-500/10 text-blue-600 dark:text-blue-300',
    amber: 'bg-amber-500/10 text-amber-600 dark:text-amber-300',
    rose: 'bg-rose-500/10 text-rose-600 dark:text-rose-300',
  }

  return (
    <article className="rounded-md border border-zinc-200 bg-white p-4 shadow-sm dark:border-white/10 dark:bg-neutral-900">
      <div className="flex items-center justify-between">
        <p className="text-sm font-medium text-zinc-500 dark:text-zinc-400">{label}</p>
        <span className={`inline-flex size-10 items-center justify-center rounded-md ${tones[tone]}`}>
          <Icon size={19} />
        </span>
      </div>
      <p className="mt-5 text-3xl font-semibold tracking-normal">{value}</p>
      <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">{detail}</p>
    </article>
  )
}

function Panel({ children, icon: Icon, id, title }) {
  return (
    <section className="rounded-md border border-zinc-200 bg-white p-4 shadow-sm dark:border-white/10 dark:bg-neutral-900" id={id}>
      <div className="mb-4 flex items-center gap-2">
        {Icon ? <Icon className="text-zinc-500 dark:text-zinc-400" size={19} /> : null}
        <h2 className="text-lg font-semibold tracking-normal">{title}</h2>
      </div>
      {children}
    </section>
  )
}

function Readout({ icon: Icon, label, value }) {
  return (
    <div className="rounded-md bg-zinc-50 p-3 dark:bg-white/5">
      <div className="flex items-center gap-2 text-xs font-medium uppercase tracking-[0.14em] text-zinc-500 dark:text-zinc-400">
        {Icon ? <Icon size={14} /> : null}
        {label}
      </div>
      <p className="mt-2 text-lg font-semibold tracking-normal">{value}</p>
    </div>
  )
}

function FuelRing({ percentage }) {
  const degrees = Math.round((percentage / 100) * 360)

  return (
    <div className="flex items-center justify-center">
      <div
        className="grid size-44 place-items-center rounded-full"
        style={{
          background: `conic-gradient(#10b981 ${degrees}deg, rgba(113, 113, 122, 0.18) 0deg)`,
        }}
      >
        <div className="grid size-32 place-items-center rounded-full bg-white text-center dark:bg-neutral-900">
          <div>
            <p className="text-4xl font-semibold">{percentage}%</p>
            <p className="text-xs uppercase tracking-[0.16em] text-zinc-500 dark:text-zinc-400">Fuel</p>
          </div>
        </div>
      </div>
    </div>
  )
}

function ChartPanel({ data, dataKey, id, stroke, title, unit }) {
  return (
    <Panel id={id} title={title} icon={Activity}>
      <div className="h-72">
        <ResponsiveContainer height="100%" width="100%">
          <LineChart data={data} margin={{ bottom: 4, left: -16, right: 12, top: 10 }}>
            <CartesianGrid stroke="rgba(113,113,122,0.18)" vertical={false} />
            <XAxis dataKey="time" fontSize={12} stroke="currentColor" tickLine={false} />
            <YAxis fontSize={12} stroke="currentColor" tickLine={false} />
            <Tooltip contentStyle={{ borderRadius: 8 }} formatter={(value) => [`${value} ${unit}`, title]} />
            <Line dataKey={dataKey} dot={false} stroke={stroke} strokeWidth={3} type="monotone" />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </Panel>
  )
}

function AreaPanel({ data, id, title }) {
  return (
    <Panel id={id} title={title} icon={Droplets}>
      <div className="h-72">
        <ResponsiveContainer height="100%" width="100%">
          <AreaChart data={data} margin={{ bottom: 4, left: -16, right: 12, top: 10 }}>
            <defs>
              <linearGradient id="fuelGradient" x1="0" x2="0" y1="0" y2="1">
                <stop offset="5%" stopColor="#10b981" stopOpacity={0.45} />
                <stop offset="95%" stopColor="#10b981" stopOpacity={0.02} />
              </linearGradient>
            </defs>
            <CartesianGrid stroke="rgba(113,113,122,0.18)" vertical={false} />
            <XAxis dataKey="time" fontSize={12} stroke="currentColor" tickLine={false} />
            <YAxis fontSize={12} stroke="currentColor" tickLine={false} />
            <Tooltip contentStyle={{ borderRadius: 8 }} formatter={(value) => [`${value}%`, 'Fuel']} />
            <Area dataKey="fuel" fill="url(#fuelGradient)" stroke="#10b981" strokeWidth={3} type="monotone" />
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </Panel>
  )
}

function DailyDistancePanel({ data }) {
  return (
    <Panel id="daily-distance" title="Daily Distance" icon={BarChart3}>
      <div className="h-72">
        <ResponsiveContainer height="100%" width="100%">
          <BarChart data={data} margin={{ bottom: 4, left: -16, right: 12, top: 10 }}>
            <CartesianGrid stroke="rgba(113,113,122,0.18)" vertical={false} />
            <XAxis dataKey="day" fontSize={12} stroke="currentColor" tickLine={false} />
            <YAxis fontSize={12} stroke="currentColor" tickLine={false} />
            <Tooltip contentStyle={{ borderRadius: 8 }} formatter={(value) => [`${value} km`, 'Distance']} />
            <Bar dataKey="km" fill="#2563eb" radius={[6, 6, 0, 0]} />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </Panel>
  )
}

function LoadingScreen() {
  return (
    <main className="grid min-h-screen place-items-center bg-neutral-950 text-white">
      <div className="text-center">
        <Gauge className="mx-auto mb-4 animate-pulse text-emerald-400" size={42} />
        <p className="text-lg font-semibold">Loading SmartFuel</p>
      </div>
    </main>
  )
}

function ErrorScreen({ message, onRetry }) {
  return (
    <main className="grid min-h-screen place-items-center bg-zinc-100 px-4 text-zinc-950">
      <div className="max-w-md rounded-md border border-zinc-200 bg-white p-6 text-center shadow-sm">
        <AlertTriangle className="mx-auto mb-4 text-amber-500" size={38} />
        <h1 className="text-xl font-semibold">Backend unavailable</h1>
        <p className="mt-2 text-sm text-zinc-600">{message}</p>
        <button className="mt-5 rounded-md bg-zinc-950 px-4 py-2 text-sm font-semibold text-white" onClick={onRetry} type="button">
          Retry
        </button>
      </div>
    </main>
  )
}

function formatDuration(seconds) {
  const minutes = Math.round(seconds / 60)
  const hours = Math.floor(minutes / 60)
  const remainingMinutes = minutes % 60

  if (hours === 0) return `${remainingMinutes} min`
  return `${hours}h ${remainingMinutes}m`
}

function formatDate(value) {
  return new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

export default App
