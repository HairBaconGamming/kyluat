/** Bộ icon nét mảnh, chép nguyên hệ từ enforcer/desktop.py để web và app giống nhau. */
const PATHS = {
  shield: (
    <>
      <path d="M12 3.5 19 6v5.5c0 4.4-3 7.4-7 9-4-1.6-7-4.6-7-9V6l7-2.5Z" />
      <path d="m9 12 2 2 4-4" />
    </>
  ),
  menu: <path d="M4 7h16M4 12h16M4 17h16" />,
  close: (
    <>
      <path d="M6.5 6.5 17.5 17.5M17.5 6.5 6.5 17.5" />
    </>
  ),
  download: (
    <>
      <path d="M12 4.5v11" />
      <path d="m7.5 11 4.5 4.5 4.5-4.5" />
      <path d="M5 19.5h14" />
    </>
  ),
  info: (
    <>
      <circle cx="12" cy="12" r="8.2" />
      <path d="M12 8v4.6M12 15.6v.1" />
    </>
  ),
  clock: (
    <>
      <circle cx="12" cy="12" r="8.2" />
      <path d="M12 7.6V12l3.2 2" />
    </>
  ),
  lock: (
    <>
      <rect x="6" y="10" width="12" height="10" rx="2" />
      <path d="M9 10V7a3 3 0 0 1 6 0v3" />
    </>
  ),
  chart: (
    <>
      <path d="M4.5 19.5h15" />
      <path d="M7.5 16V10M12 16V5.5M16.5 16v-4" />
    </>
  ),
  desktop: (
    <>
      <rect x="3.5" y="4.5" width="17" height="11" rx="1.8" />
      <path d="M8.5 19.5h7M12 15.5v4" />
    </>
  ),
  phone: (
    <>
      <rect x="7" y="2.5" width="10" height="19" rx="2" />
      <path d="M11 18.5h2" />
    </>
  ),
  dumbbell: <path d="M5 9.5h14M5 14.5h14M3.2 7v10M20.8 7v10" />,
  power: (
    <>
      <path d="M12 4.4v6.4" />
      <path d="M7.6 7a6.6 6.6 0 1 0 8.8 0" />
    </>
  ),
  alert: (
    <>
      <path d="M12 4.6 20.2 19H3.8Z" />
      <path d="M12 10.2v3.4M12 16.2v.1" />
    </>
  ),
};

export default function Icon({ name, className, style }) {
  if (!PATHS[name]) return null;
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className={className}
      style={style}
    >
      {PATHS[name]}
    </svg>
  );
}
