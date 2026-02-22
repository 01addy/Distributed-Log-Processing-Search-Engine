export default function StatCard({ label, value, sub, icon: Icon, color = 'accent', trend }) {
  const colors = {
    accent: 'text-accent border-accent/20 bg-accent/5',
    danger: 'text-danger border-danger/20 bg-danger/5',
    success: 'text-success border-success/20 bg-success/5',
    warning: 'text-warning border-warning/20 bg-warning/5',
  }
  return (
    <div className="bg-panel border border-border rounded-xl p-5 hover:border-accent/20 transition-all duration-300 group animate-slide-up">
      <div className="flex items-start justify-between mb-3">
        <span className="text-xs text-muted font-mono uppercase tracking-widest">{label}</span>
        {Icon && (
          <div className={`w-8 h-8 rounded-lg border flex items-center justify-center ${colors[color]}`}>
            <Icon size={14} />
          </div>
        )}
      </div>
      <div className={`text-3xl font-display font-bold ${colors[color].split(' ')[0]} mb-1`}>{value}</div>
      {sub && <div className="text-xs text-muted">{sub}</div>}
    </div>
  )
}
