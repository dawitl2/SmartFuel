insert into public.users (id, full_name, email)
values ('00000000-0000-0000-0000-000000000001', 'Demo Driver', 'demo@smartfuel.local')
on conflict (id) do nothing;

insert into public.cars (
  id,
  user_id,
  name,
  make,
  model,
  year,
  tank_capacity_liters,
  consumption_l_per_km,
  max_range_km
)
values (
  '10000000-0000-0000-0000-000000000001',
  '00000000-0000-0000-0000-000000000001',
  'Daily Driver',
  'Toyota',
  'Corolla',
  2008,
  40,
  0.1,
  400
)
on conflict (id) do nothing;

insert into public.fuel_state (
  car_id,
  fuel_remaining_liters,
  fuel_used_liters,
  estimated_range_km,
  fuel_percentage,
  last_full_reset_at
)
values (
  '10000000-0000-0000-0000-000000000001',
  26.8,
  13.2,
  268,
  67,
  now() - interval '5 days'
)
on conflict (car_id) do nothing;

insert into public.settings (car_id)
values ('10000000-0000-0000-0000-000000000001')
on conflict (car_id) do nothing;
