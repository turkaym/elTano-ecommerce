import { useState } from 'react'

export function SearchBar() {
  const [value, setValue] = useState('')

  return (
    <form
      className="search-bar"
      role="search"
      onSubmit={(event) => {
        event.preventDefault()
      }}
    >
      <label htmlFor="storefront-search" className="search-label">
        Buscar productos
      </label>
      <input
        id="storefront-search"
        type="search"
        placeholder="Buscar frutos secos, harinas, semillas..."
        value={value}
        onChange={(event) => setValue(event.target.value)}
      />
    </form>
  )
}
