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
        <NavLink key={link.to} to={link.to} className="category-pill">
          {link.label}
        </NavLink>
      ))}
    </nav>
  )
}
