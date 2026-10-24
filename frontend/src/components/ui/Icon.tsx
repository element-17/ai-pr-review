const iconPaths = {
  branch:
    'M6 3v12m0-8a4 4 0 0 0 4 4h4a4 4 0 0 0 4-4V3M6 15a3 3 0 1 0 0 6 3 3 0 0 0 0-6ZM18 3a2 2 0 1 0 0 4 2 2 0 0 0 0-4Z',
  plus: 'M12 5v14M5 12h14',
  history: 'M3 11a9 9 0 1 1 2.6 7M3 4v7h7m2-4v5l3 2',
  settings: 'M4 7h16M4 17h16M8 4v6m8 4v6',
  file: 'M14 2H5v20h14V7l-5-5Zm0 0v6h5M8 13h8m-8 4h5',
  shield: 'm12 3 8 3v6c0 5-8 9-8 9S4 17 4 12V6l8-3Zm-4 9 3 3 5-6',
  arrow: 'M5 12h14m-5-5 5 5-5 5',
  chevron: 'm9 5 7 7-7 7',
  check: 'm5 12 4 4L19 6',
  globe:
    'M3 12h18M12 3a17 17 0 0 1 0 18 17 17 0 0 1 0-18ZM21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z',
  sun: 'M12 2v2m0 16v2M2 12h2m16 0h2M5 5l2 2m10 10 2 2M5 19l2-2M17 7l2-2m-3 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z',
  moon: 'M20.5 14A9 9 0 0 1 10 3.5 9 9 0 1 0 20.5 14Z',
  alert: 'm12 3 10 18H2L12 3Zm0 6v5m0 3v1',
  code: 'm8 6-6 6 6 6m8-12 6 6-6 6m-3-15-2 18',
  download: 'M12 3v12m-5-5 5 5 5-5M4 16v5h16v-5',
  compare: 'M7 3v18m10 0V3M3 7l4-4 4 4m2 10 4 4 4-4',
  external: 'M14 3h7v7m0-7L10 14m0-11H3v18h18v-7',
  search: 'm16 16 5 5M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0Z',
}

export function Icon({
  name,
  size = 20,
}: {
  name: keyof typeof iconPaths
  size?: number
}) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d={iconPaths[name]} />
    </svg>
  )
}
