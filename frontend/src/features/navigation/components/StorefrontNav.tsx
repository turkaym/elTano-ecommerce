import { NavLink } from 'react-router-dom'

const links = [
  { label: 'Inicio', to: '/' },
  { label: 'Categorías', to: '/categorias' },
  { label: 'Productos', to: '/productos' },
]

export function StorefrontNav() {
  return (
    <nav className="category-nav" aria-label="Categorías">
      {links.map((link) => (
        <NavLink
          key={link.to}
          to={link.to}
          end={link.to === '/'}
          className={({ isActive }) =>
            isActive ? 'category-pill category-pill-active' : 'category-pill'
          }
        >
          {link.label}
        </NavLink>
      ))}
    </nav>
  )
}
