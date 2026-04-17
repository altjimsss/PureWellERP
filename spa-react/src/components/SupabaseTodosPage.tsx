import { useEffect, useMemo, useState } from 'react'
import { createClient } from '../utils/supabase/client'

type Todo = {
  id: number | string
  name: string
}

const SupabaseTodosPage = () => {
  const supabase = useMemo(() => createClient(), [])
  const [todos, setTodos] = useState<Todo[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const run = async () => {
      setLoading(true)
      setError(null)

      const { data, error: queryError } = await supabase
        .from('todos')
        .select('id,name')
        .order('id', { ascending: true })

      if (queryError) {
        setError(queryError.message)
        setTodos([])
      } else {
        setTodos((data as Todo[]) ?? [])
      }

      setLoading(false)
    }

    void run()
  }, [supabase])

  return (
    <section style={{ padding: '1rem' }}>
      <h2>Supabase Todos</h2>
      <p style={{ marginBottom: '1rem' }}>
        This reads from the public todos table via Supabase client SDK.
      </p>
      {loading && <p>Loading...</p>}
      {!loading && error && <p style={{ color: '#b00020' }}>Error: {error}</p>}
      {!loading && !error && (
        <ul>
          {todos.map((todo) => (
            <li key={todo.id}>{todo.name}</li>
          ))}
        </ul>
      )}
    </section>
  )
}

export default SupabaseTodosPage
