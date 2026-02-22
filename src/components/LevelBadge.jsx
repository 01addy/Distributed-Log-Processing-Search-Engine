export default function LevelBadge({ level, size = 'sm' }) {
  const sizes = { xs: 'text-[9px] px-1.5 py-0.5', sm: 'text-[10px] px-2 py-0.5', md: 'text-xs px-2.5 py-1' }
  return (
    <span className={`level-${level} inline-flex items-center font-mono font-500 rounded border uppercase tracking-widest ${sizes[size]}`}>
      {level}
    </span>
  )
}
