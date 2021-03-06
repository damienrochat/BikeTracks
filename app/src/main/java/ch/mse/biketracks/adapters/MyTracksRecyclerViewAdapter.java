package ch.mse.biketracks.adapters;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import ch.mse.biketracks.R;
import ch.mse.biketracks.models.Track;
import ch.mse.biketracks.utils.MyTools;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class MyTracksRecyclerViewAdapter extends RecyclerView.Adapter<MyTracksRecyclerViewAdapter.ViewHolder> {

    private final List<Track> mValues;

    public MyTracksRecyclerViewAdapter(List<Track> items) {
        mValues = items;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.fragment_tracks, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        holder.mItem = mValues.get(position);
        Track track = holder.mItem;
        holder.location.setText(track.getName());
        holder.distance.setText(String.format(Locale.ENGLISH, "%.1f km", track.getDistance()/ 1000.));
        holder.speed.setText(String.format(Locale.ENGLISH,"%.1f km/h",track.getSpeed() * 3.6));
        byte[] imageByteArray = track.getImage();
        if (imageByteArray != null) {
            Bitmap bm = BitmapFactory.decodeByteArray(imageByteArray, 0, imageByteArray.length);
            holder.image.setImageBitmap(bm);
        }

        // TODO : Support multiple formats of dates depending on locale
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.ENGLISH);
        holder.date.setText(sdf.format(mValues.get(position).getDate()));
        holder.duration.setText(MyTools.FormatTimeHHhmm(track.getDuration()));
    }

    @Override
    public int getItemCount() {
        return mValues.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public final View mView;
        public final TextView location;
        public final TextView date;
        public final TextView distance;
        public final TextView duration;
        public final TextView speed;
        public final ImageView image;
        public Track mItem;

        public ViewHolder(View view) {
            super(view);
            mView = view;
            location = (TextView) view.findViewById(R.id.location);
            date = (TextView) view.findViewById(R.id.date);
            distance = (TextView) view.findViewById(R.id.distance);
            duration = (TextView) view.findViewById(R.id.duration);
            speed = (TextView) view.findViewById(R.id.speed);
            image = (ImageView) view.findViewById(R.id.image_track);
        }

        @Override
        public String toString() {
            return super.toString() + " '" + location.getText() + "'";
        }
    }
}
