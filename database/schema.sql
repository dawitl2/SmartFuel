create extension if not exists "pgcrypto";

create type fuel_event_type as enum ('full_reset', 'partial_refuel');
create type maintenance_status as enum ('scheduled', 'due', 'completed', 'overdue');
create type notification_type as enum ('low_fuel', 'oil_change', 'long_idle', 'high_rpm', 'vehicle_offline', 'maintenance_due');
create type notification_severity as enum ('info', 'warning', 'critical');

create table public.users (
  id uuid primary key references auth.users(id) on delete cascade,
  full_name text,
  email text not null unique,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.cars (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.users(id) on delete cascade,
  name text not null,
  make text,
  model text,
  year integer check (year between 1980 and extract(year from now())::integer + 1),
  vin text unique,
  tank_capacity_liters numeric(6,2) not null default 40 check (tank_capacity_liters > 0),
  consumption_l_per_km numeric(8,4) not null default 0.1 check (consumption_l_per_km > 0),
  max_range_km numeric(8,2) not null default 400 check (max_range_km > 0),
  device_token_hash text,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.trips (
  id uuid primary key default gen_random_uuid(),
  car_id uuid not null references public.cars(id) on delete cascade,
  started_at timestamptz not null,
  ended_at timestamptz,
  distance_km numeric(10,3) not null default 0 check (distance_km >= 0),
  driving_seconds integer not null default 0 check (driving_seconds >= 0),
  idle_seconds integer not null default 0 check (idle_seconds >= 0),
  average_speed_kph numeric(8,2) not null default 0 check (average_speed_kph >= 0),
  max_speed_kph numeric(8,2) not null default 0 check (max_speed_kph >= 0),
  average_rpm numeric(8,2) not null default 0 check (average_rpm >= 0),
  fuel_used_liters numeric(8,3) not null default 0 check (fuel_used_liters >= 0),
  created_at timestamptz not null default now()
);

create table public.telemetry_logs (
  id uuid primary key default gen_random_uuid(),
  car_id uuid not null references public.cars(id) on delete cascade,
  trip_id uuid references public.trips(id) on delete set null,
  recorded_at timestamptz not null,
  speed_kph numeric(8,2) not null check (speed_kph >= 0),
  rpm numeric(8,2) check (rpm >= 0),
  distance_increment_km numeric(10,4) not null default 0 check (distance_increment_km >= 0),
  estimated_odometer_km numeric(12,3) check (estimated_odometer_km >= 0),
  engine_load_percent numeric(5,2) check (engine_load_percent between 0 and 100),
  coolant_temp_c numeric(5,2),
  battery_voltage numeric(5,2),
  latitude numeric(10,7),
  longitude numeric(10,7),
  raw_payload jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create table public.fuel_state (
  car_id uuid primary key references public.cars(id) on delete cascade,
  fuel_remaining_liters numeric(8,3) not null check (fuel_remaining_liters >= 0),
  fuel_used_liters numeric(8,3) not null default 0 check (fuel_used_liters >= 0),
  estimated_range_km numeric(8,2) not null default 0 check (estimated_range_km >= 0),
  fuel_percentage numeric(5,2) not null check (fuel_percentage between 0 and 100),
  last_full_reset_at timestamptz,
  updated_at timestamptz not null default now()
);

create table public.refuel_events (
  id uuid primary key default gen_random_uuid(),
  car_id uuid not null references public.cars(id) on delete cascade,
  event_type fuel_event_type not null,
  liters_added numeric(8,3) not null default 0 check (liters_added >= 0),
  fuel_after_liters numeric(8,3) not null check (fuel_after_liters >= 0),
  odometer_km numeric(12,3) check (odometer_km >= 0),
  note text,
  created_at timestamptz not null default now()
);

create table public.maintenance_records (
  id uuid primary key default gen_random_uuid(),
  car_id uuid not null references public.cars(id) on delete cascade,
  title text not null,
  description text,
  due_odometer_km numeric(12,3),
  due_at date,
  completed_at timestamptz,
  status maintenance_status not null default 'scheduled',
  created_at timestamptz not null default now()
);

create table public.settings (
  car_id uuid primary key references public.cars(id) on delete cascade,
  low_fuel_threshold_percent numeric(5,2) not null default 15 check (low_fuel_threshold_percent between 1 and 50),
  high_rpm_threshold numeric(8,2) not null default 4200 check (high_rpm_threshold > 0),
  long_idle_threshold_seconds integer not null default 300 check (long_idle_threshold_seconds > 0),
  offline_threshold_minutes integer not null default 30 check (offline_threshold_minutes > 0),
  distance_unit text not null default 'km' check (distance_unit in ('km', 'mi')),
  theme text not null default 'system' check (theme in ('light', 'dark', 'system')),
  updated_at timestamptz not null default now()
);

create table public.notifications (
  id uuid primary key default gen_random_uuid(),
  car_id uuid not null references public.cars(id) on delete cascade,
  type notification_type not null,
  severity notification_severity not null default 'info',
  title text not null,
  body text not null,
  is_read boolean not null default false,
  created_at timestamptz not null default now()
);

create index telemetry_logs_car_recorded_at_idx on public.telemetry_logs (car_id, recorded_at desc);
create index telemetry_logs_trip_idx on public.telemetry_logs (trip_id);
create index trips_car_started_at_idx on public.trips (car_id, started_at desc);
create index refuel_events_car_created_at_idx on public.refuel_events (car_id, created_at desc);
create index maintenance_records_car_status_idx on public.maintenance_records (car_id, status);
create index notifications_car_created_at_idx on public.notifications (car_id, created_at desc);
create index notifications_unread_idx on public.notifications (car_id, is_read) where is_read = false;
