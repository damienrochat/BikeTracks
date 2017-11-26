package ch.mse.biketracks.adapters;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import ch.mse.biketracks.R;
import ch.mse.biketracks.database.ContactContract;
import ch.mse.biketracks.database.ContactDbHelper;
import ch.mse.biketracks.models.Contact;

import java.util.List;

public class MySettingsRecyclerViewAdapter extends RecyclerView.Adapter<MySettingsRecyclerViewAdapter.ViewHolder> {

    private final List<Contact> mValues;
    private final Context context;

    public MySettingsRecyclerViewAdapter(List<Contact> contacts, Context context) {
        mValues = contacts;
        this.context = context;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.fragment_settings, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        holder.mItem = mValues.get(position);
        holder.name.setText(mValues.get(position).getName());
        holder.phoneNumber.setText(mValues.get(position).getPhoneNumber());

        holder.delete.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                int position = holder.getAdapterPosition();
                Contact removedContact = mValues.get(position);
                // Remove item from database

                ContactDbHelper mDbHelper = new ContactDbHelper(context);
                SQLiteDatabase db = mDbHelper.getReadableDatabase();
                // Define 'where' part of query.
                String selection = ContactContract.ContactEntry.COLUMN_NAME_NAME + " = ?";
                // Specify arguments in placeholder order.
                String[] selectionArgs = { removedContact.getName() };
                // Issue SQL statement.
                db.delete(ContactContract.ContactEntry.TABLE_NAME, selection, selectionArgs);
                // Remove item from list
                mValues.remove(position);  // remove the item from list
                notifyItemRemoved(position); // notify the adapter about the removed item
            }
        });
    }

    @Override
    public int getItemCount() {
        return mValues.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public final View mView;
        public final TextView name;
        public final TextView phoneNumber;
        public final FloatingActionButton delete;
        public Contact mItem;

        public ViewHolder(View view) {
            super(view);
            mView = view;
            name = (TextView) view.findViewById(R.id.name);
            phoneNumber = (TextView) view.findViewById(R.id.phone_number);
            delete = (FloatingActionButton) view.findViewById(R.id.delete);
        }

        @Override
        public String toString() {
            return super.toString() + " '" + name.getText() + "'";
        }
    }
}